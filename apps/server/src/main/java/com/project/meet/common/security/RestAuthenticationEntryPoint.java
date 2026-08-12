package com.project.meet.common.security;

import tools.jackson.databind.ObjectMapper;
import com.project.meet.common.exception.ApiError;
import com.project.meet.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Requests rejected by the Spring Security filter chain never reach
 * {@code GlobalExceptionHandler} (that only covers exceptions thrown inside
 * the DispatcherServlet), so the same ApiError JSON shape is written here.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ApiError error = ApiError.of("UNAUTHENTICATED", "Bạn cần đăng nhập để truy cập tài nguyên này",
				MDC.get(RequestIdFilter.MDC_KEY));
		objectMapper.writeValue(response.getWriter(), error);
	}
}
