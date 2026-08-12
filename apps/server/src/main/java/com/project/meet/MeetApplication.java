package com.project.meet;

import me.paulschwarz.springdotenv.spring.DotenvApplicationInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MeetApplication {

	public static void main(String[] args) {
		new SpringApplicationBuilder(MeetApplication.class)
				.initializers(new DotenvApplicationInitializer())
				.run(args);
	}

}
