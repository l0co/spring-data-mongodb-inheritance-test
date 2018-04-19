package com.example.demo.repository;

import com.example.demo.domain.Thing;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ThingRepository<T extends Thing> extends CrudRepository<T, String> {

	Optional<T> findByName(String name);

	@Query("{'name': ?0, '_class': #{#entityName}}")
	Optional<T> queryByName(String name);

}
