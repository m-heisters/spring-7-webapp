package guru.springframework.spring7restmvc.repositories;

import guru.springframework.spring7restmvc.entities.Beer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("localmysql")
@SpringBootTest
public class MySqlTest {

    @Container
    private MySQLContainer mySQLContainer = new MySQLContainer("mysql:9");

    @Autowired
    DataSource dataSource;

    @Autowired
    BeerRepository beerRepository;

    @Test
    void listBeers() {
        List<Beer> beers = beerRepository.findAll();

        assertThat(beers).hasSizeGreaterThan(0);
    }
}
