package com.library.userservice.security;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Chỉ nhận Bearer trong Authorization, không dùng cookie/query hoặc header danh tính khác. */
public class UserBearerAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern BEARER = Pattern.compile("^Bearer ([A-Za-z0-9\\-._~+/]+=*)$", Pattern.CASE_INSENSITIVE);
    private final UserAccessTokenService tokens;
    private final UserSessionAuthenticator sessions;
    private final SecurityErrorWriter errors;

    public UserBearerAuthenticationFilter(UserAccessTokenService tokens, UserSessionAuthenticator sessions, SecurityErrorWriter errors) {
        this.tokens = tokens; this.sessions = sessions; this.errors = errors;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) { chain.doFilter(request, response); return; }
        try {
            if (headers.size() != 1) throw new ApplicationException(ErrorCode.ACCESS_TOKEN_INVALID);
            var matcher = BEARER.matcher(headers.getFirst());
            if (!matcher.matches()) throw new ApplicationException(ErrorCode.ACCESS_TOKEN_INVALID);
            var principal = sessions.authenticate(tokens.verify(matcher.group(1)));
            var authorities = principal.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
            SecurityContextHolder.setContext(context);
        } catch (ApplicationException exception) {
            SecurityContextHolder.clearContext(); errors.write(response, exception.code()); return;
        } catch (TransientDataAccessException | DataAccessResourceFailureException
                | org.springframework.transaction.CannotCreateTransactionException exception) {
            fail(response, ErrorCode.SERVICE_UNAVAILABLE, exception); return;
        } catch (Exception exception) {
            fail(response, ErrorCode.INTERNAL_ERROR, exception); return;
        }
        // Không bắt lỗi của controller để tránh đổi lỗi MVC/nghiệp vụ thành lỗi xác thực.
        chain.doFilter(request, response);
    }

    private void fail(HttpServletResponse response, ErrorCode code, Exception exception) throws IOException {
        SecurityContextHolder.clearContext();
        logger.error("Không xác thực được request, loại lỗi: " + exception.getClass().getName());
        errors.write(response, code);
    }
}
