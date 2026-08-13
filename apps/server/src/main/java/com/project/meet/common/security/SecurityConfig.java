package com.project.meet.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
	private final RestAccessDeniedHandler restAccessDeniedHandler;

	public SecurityConfig(
			JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint restAuthenticationEntryPoint,
			RestAccessDeniedHandler restAccessDeniedHandler
	) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
		this.restAccessDeniedHandler = restAccessDeniedHandler;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// CORS preflight requests never carry the JWT (browsers strip
						// custom headers/credentials from OPTIONS preflights), so they
						// must be let through regardless of the target route's auth
						// requirement — otherwise the browser never sends the real
						// request to any protected endpoint at all.
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/api/v1/auth/**").permitAll()
						// The WebSocket handshake authenticates itself via a JWT query
						// param, verified in SignalingHandshakeInterceptor — browsers
						// can't attach an Authorization header to a WS upgrade request,
						// so this can't go through the same JwtAuthenticationFilter path.
						.requestMatchers("/ws/**").permitAll()
						// HLS output and livestream status are watched by
						// viewers who aren't meeting participants at all —
						// there's nothing to authenticate them against.
						.requestMatchers(HttpMethod.GET, "/livestreams/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/meetings/*/livestream").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/meetings/code/*/livestream").permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(restAuthenticationEntryPoint)
						.accessDeniedHandler(restAccessDeniedHandler)
				)
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
