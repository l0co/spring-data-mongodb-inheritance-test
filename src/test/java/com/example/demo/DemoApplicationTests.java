package com.example.demo;

import com.example.demo.domain.Boat;
import com.example.demo.domain.Car;
import com.example.demo.domain.Thing;
import com.example.demo.repository.BoatRepository;
import com.example.demo.repository.CarRepository;
import com.example.demo.repository.ThingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class DemoApplicationTests {

    @Autowired private ThingRepository thingRepository;
    @Autowired private CarRepository carRepository;
    @Autowired private BoatRepository boatRepository;

    @Before
    public void initData() {
        if (thingRepository.count()==0) {

            Thing thing = new Thing();
            thing.setName("A Knife");
            thingRepository.save(thing);

            Car honda = new Car();
            honda.setModel("Honda Civic");
            honda.setName("A Car");
            carRepository.save(honda);

            Boat enterprise = new Boat();
            enterprise.setLength(10);
            enterprise.setName("A Boat");
            boatRepository.save(enterprise);

        }
    }

    @Test
    public void checkInheritanceAwareStuff() {
        // We should have 2 things in the collection
        assertThat(thingRepository.count()).isEqualTo(3);

        // But only one of each specific types of things
        assertThat(carRepository.count()).isEqualTo(1);
        assertThat(boatRepository.count()).isEqualTo(1);

        // And the generated queries should work correctly as well
        assertThat(carRepository.findCarsByIdNotNull().size()).isEqualTo(1);
        assertThat(carRepository.countCarsByIdNotNull()).isEqualTo(1);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void checkRepositoryInheritance() {
        assertThat(thingRepository.findByName("A Knife").get()).isOfAnyClassIn(Thing.class);
        assertThat(thingRepository.findByName("A Car").get()).isOfAnyClassIn(Car.class);
        assertThat(thingRepository.findByName("A Boat").get()).isOfAnyClassIn(Boat.class);

        assertThat(thingRepository.queryByName("A Knife").get()).isOfAnyClassIn(Thing.class);
        assertThat(carRepository.queryByName("A Car").get()).isOfAnyClassIn(Car.class);
        assertThat(boatRepository.queryByName("A Boat").get()).isOfAnyClassIn(Boat.class);
    }

}

