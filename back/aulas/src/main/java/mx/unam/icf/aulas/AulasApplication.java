package mx.unam.icf.aulas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AulasApplication {

    public static void main(String[] args) {
        Short o = 1;
        var p = o.doubleValue();
        SpringApplication.run(AulasApplication.class, args);
    }



}
