package com.project.meet;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MeetApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(MeetApplication.class)
				.initializers(new DotenvApplicationInitializer())
				.run(args);
	}

}
