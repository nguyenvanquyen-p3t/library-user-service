package com.library.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "debug=false")
class UserServiceApplicationTests extends com.library.userservice.security.UserSecurityTestKeys {

	@Test
	void contextLoads() {
	}

}
