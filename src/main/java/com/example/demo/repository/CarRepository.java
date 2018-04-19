package com.example.demo.repository;

import com.example.demo.domain.Car;

import java.util.List;

public interface CarRepository extends ThingRepository<Car> {

    List<Car> findCarsByIdNotNull();

    long countCarsByIdNotNull();

}
