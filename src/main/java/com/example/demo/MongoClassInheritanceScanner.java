package com.example.demo;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.lang.Nullable;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Calculates subclasses for any mongo entity class.
 *
 * @author Lukasz Frankowski (http://lifeinide.com)
 */
public class MongoClassInheritanceScanner {

	protected List<Class> classes = Collections.synchronizedList(new ArrayList<>());
	protected Map<Class, List<Class>> allClasses = new ConcurrentHashMap<>();
	protected Map<Class, List<String>> aliases = new ConcurrentHashMap<>();

	private static MongoClassInheritanceScanner instance;

	private MongoClassInheritanceScanner() {
		ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new AnnotationTypeFilter(TypeAlias.class));
		provider.findCandidateComponents("com.example").forEach(it -> {
			try {
				classes.add(Class.forName(it.getBeanClassName()));
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		});
	}

	public static MongoClassInheritanceScanner getInstance() {
		if (instance==null)
			instance = new MongoClassInheritanceScanner();

		return instance;
	}

	/**
	 * All classes of clazz, together with subclasses.
	 */
	@SuppressWarnings("unchecked")
	public List<Class> getAllClasses(Class clazz) {
		return allClasses.computeIfAbsent(clazz, clazz1 -> classes.stream()
			.filter(clazz1::isAssignableFrom)
			.collect(Collectors.toList()));
	}

	/**
	 * All aliases of clazz, together with subclass aliases.
	 */
	@SuppressWarnings("unchecked")
	public List<String> getAliases(Class clazz) {
		return aliases.computeIfAbsent(clazz, clazz1 -> getAllClasses(clazz1).stream()
			.map(this::findAlias)
			.filter(Objects::nonNull)
			.collect(Collectors.toList()));
	}

	public String findAlias(Class<?> clazz) {
		return (!Modifier.isAbstract(clazz.getModifiers()) && clazz.isAnnotationPresent(TypeAlias.class))
			? clazz.getAnnotation(TypeAlias.class).value()
			: null;
	}

	@Nullable
	public Criteria createInheritanceCritera(List<String> aliases) {
		if (!aliases.isEmpty()) {
			return new Criteria().orOperator(aliases.stream()
				.map(alias -> where("_class").is(alias))
				.toArray(Criteria[]::new));
		}

		return null;
	}

	@Nullable
	public Criteria createInheritanceCritera(Class<?> clazz) {
		return createInheritanceCritera(getAliases(clazz));
	}

}
