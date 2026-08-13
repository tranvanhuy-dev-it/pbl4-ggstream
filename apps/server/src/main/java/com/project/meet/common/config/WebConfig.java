package com.project.meet.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final CorsProperties corsProperties;
	private final LivestreamProperties livestreamProperties;

	public WebConfig(CorsProperties corsProperties, LivestreamProperties livestreamProperties) {
		this.corsProperties = corsProperties;
		this.livestreamProperties = livestreamProperties;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(corsProperties.allowedOrigins().toArray(new String[0]))
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true);
	}

	/**
	 * Serves the .m3u8/.ts files FFmpeg writes (see FfmpegProcessLauncher)
	 * as plain static files — HLS is just a playlist + segment files over
	 * HTTP, no custom controller needed for the viewer to fetch them.
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = "file:" + Paths.get(livestreamProperties.outputDir()).toAbsolutePath() + "/";
		registry.addResourceHandler("/livestreams/**").addResourceLocations(location);
	}
}
