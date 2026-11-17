package com.library.userservice.security;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authorization dành cho Service JWT tương lai; hiện chỉ xác thực actor qua header riêng. */
public class AdminActorAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Admin-Access-Token";
    private final AdminActorAuthenticator actors;
    private final SecurityErrorWriter errors;

    public AdminActorAuthenticationFilter(AdminActorAuthenticator actors, SecurityErrorWriter errors) {
        this.actors = actors; this.errors = errors;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            var headers = Collections.list(request.getHeaders(HEADER));
            if (headers.isEmpty()) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (headers.size() != 1 || !headers.getFirst().matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
                throw new ApplicationException(ErrorCode.ADMIN_ACTOR_INVALID);
            var actor = actors.authenticate(headers.getFirst());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            SecurityContextHolder.setContext(context);
        } catch (ApplicationException exception) {
            SecurityContextHolder.clearContext(); errors.write(response, exception.code()); return;
        } catch (TransientDataAccessException | DataAccessResourceFailureException | CannotCreateTransactionException exception) {
            fail(response, ErrorCode.SERVICE_UNAVAILABLE, exception); return;
        } catch (Exception exception) {
            fail(response, ErrorCode.INTERNAL_ERROR, exception); return;
        }
        chain.doFilter(request, response);
    }

    private void fail(HttpServletResponse response, ErrorCode code, Exception exception) throws IOException {
        SecurityContextHolder.clearContext();
        logger.error("Không xác thực được actor Admin, loại lỗi: " + exception.getClass().getName());
        errors.write(response, code);
    }
}
