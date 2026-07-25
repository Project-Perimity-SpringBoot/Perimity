package com.perimity.gatepass;


import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class GatepassServiceApplication {

	public static void main(String[] args) {
		  TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(GatepassServiceApplication.class, args);
	}

}
