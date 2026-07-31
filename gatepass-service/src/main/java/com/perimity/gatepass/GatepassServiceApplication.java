package com.perimity.gatepass;


import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling   // required for PassExpirySweep; without it the sweep never runs
public class GatepassServiceApplication {

	public static void main(String[] args) {
		  TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(GatepassServiceApplication.class, args);
	}

}
