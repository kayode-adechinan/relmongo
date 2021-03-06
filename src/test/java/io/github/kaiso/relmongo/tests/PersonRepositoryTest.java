package io.github.kaiso.relmongo.tests;

import io.github.kaiso.relmongo.data.model.Car;
import io.github.kaiso.relmongo.data.model.Color;
import io.github.kaiso.relmongo.data.model.DrivingLicense;
import io.github.kaiso.relmongo.data.model.House;
import io.github.kaiso.relmongo.data.model.Passport;
import io.github.kaiso.relmongo.data.model.Person;
import io.github.kaiso.relmongo.data.repository.CarRepository;
import io.github.kaiso.relmongo.data.repository.DrivingLicenseRepository;
import io.github.kaiso.relmongo.data.repository.HouseRepository;
import io.github.kaiso.relmongo.data.repository.PassportRepository;
import io.github.kaiso.relmongo.data.repository.PersonRepository;
import io.github.kaiso.relmongo.lazy.LazyLoadingProxy;
import io.github.kaiso.relmongo.tests.common.AbstractBaseTest;
import io.github.kaiso.relmongo.util.RelMongoConstants;

import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PersonRepositoryTest extends AbstractBaseTest {

    @Autowired
    private PersonRepository repository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private HouseRepository houseRepository;

    @Autowired
    private PassportRepository passportRepository;

    @Autowired
    private DrivingLicenseRepository drivingLicenseRepository;


    @Test
    public void shouldSaveAndRetreive() {
        Person employee = new Person();
        employee.setName("Dave");
        employee.setEmail("dave@mail.com");
        repository.save(employee);
        assertNotNull(employee.getId());
        Optional<Person> retreivedEmployee = repository.findById(employee.getId().toString());
        assertTrue(retreivedEmployee.isPresent());
        assertEquals(retreivedEmployee.get().getId(), employee.getId());
    }
    
    @Test()
    public void shouldFailOnIndexedField() {
        Assertions.assertThrows(DuplicateKeyException.class, () -> {
            Car car1 = new Car(1);
            car1.setColor(Color.BLUE);
            car1.setManufacturer("BMW");
            Car car2 = new Car(1);
            car2.setColor(Color.RED);
            car2.setManufacturer("BMW");
            
            carRepository.save(car1);
            carRepository.save(car2);
          });
    }

    @Test
    public void shouldPersistOnlyIdOnOneToManyRelation() {
        Car car1 = new Car(1);
        car1.setColor(Color.BLUE);
        car1.setManufacturer("BMW");
        Car car2 = new Car(2);
        car2.setColor(Color.RED);
        car2.setManufacturer("BMW");

        carRepository.save(car1);
        carRepository.save(car2);

        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setCars(Arrays.asList(new Car[] { car1, car2 }));
        repository.save(person);

        Document document = mongoOperations.getCollection("people").find().iterator().next();
        assertNull(document.get("cars"));
        Document obj = new Document();
        obj.put("_id", car1.getId());
        obj.put(RelMongoConstants.RELMONGOTARGET_PROPERTY_NAME, "car");
        assertTrue(((Collection<?>) document.get("carsrefs")).size() == 2);
        assertEquals(((Collection<?>) document.get("carsrefs")).iterator().next(), obj);
    }

    @Test
    public void shouldPersistOnlyReferencedPropertyOnOneToOneRelation() {
        Passport passport = new Passport();
        passport.setNumber("12345");
        passportRepository.save(passport);
        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setPassport(passport);
        repository.save(person);
        Document document = mongoOperations.getCollection("people").find().iterator().next();
        System.out.println("db id" + document.get("passportId"));
        System.out.println("relational id" + passport.getId());
        Document obj = new Document();
        obj.put("_id", passport.getId());
        obj.put(RelMongoConstants.RELMONGOTARGET_PROPERTY_NAME, "passport");
        assertEquals(document.get("passport"), obj);
    }

    @Test
    public void shouldfetchOneToOneRelation() {
        Passport passport = new Passport();
        passport.setNumber("12345");
        passportRepository.save(passport);
        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setPassport(passport);
        repository.save(person);
        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());
        assertNotNull(retreivedPerson.get().getPassport());
        assertEquals(retreivedPerson.get().getPassport().getNumber(), "12345");
        // mappedBy
        assertEquals(retreivedPerson.get().getPassport().getOwner().getId(), retreivedPerson.get().getId());

    }

    @Test
    public void shouldFetchOneToManyRelation() {
        Car car = new Car(1);
        car.setColor(Color.BLUE);
        String manufacturer = "BMW";
        car.setManufacturer(manufacturer);
        // carRepository.save(car);

        Car car1 = new Car(2);
        car1.setColor(Color.RED);
        String manufacturer1 = "JAGUAR";
        car1.setManufacturer(manufacturer1);
        // carRepository.save(car1);

        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setCars(Arrays.asList(new Car[] { car, car1 }));
        repository.save(person);
        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());
        System.out.println(retreivedPerson.get());
        assertFalse(retreivedPerson.get().getCars().isEmpty());
        assertTrue(retreivedPerson.get().getCars().get(0).getColor().equals(Color.BLUE));
        assertTrue(retreivedPerson.get().getCars().get(0).getManufacturer().equals(manufacturer));
        // mappedBy
        assertEquals(retreivedPerson.get().getCars().get(0).getOwner().getId(), retreivedPerson.get().getId());
    }

    @Test
    public void shouldLazyLoadCollection() {
        House house = new House("H4");
        house.setAddress("Paris");
        houseRepository.save(house);
        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setHouses(Arrays.asList(new House[] { house }));
        repository.save(person);
        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());
        System.out.println(retreivedPerson.get());
        assertFalse(retreivedPerson.get().getHouses().isEmpty());
        assertTrue(retreivedPerson.get().getHouses().get(0).getAddress().equals("Paris"));
        assertTrue(retreivedPerson.get().getHouses() instanceof LazyLoadingProxy);
        // mappedBy
        assertEquals(retreivedPerson.get().getHouses().get(0).getOwner().getId(), retreivedPerson.get().getId());
    }

    @Test
    public void shouldLazyLoadObject() {
        DrivingLicense drivingLicense = new DrivingLicense("ZUY0001");
        drivingLicense.setNumber("12345");
        drivingLicenseRepository.save(drivingLicense);
        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setDrivingLicense(drivingLicense);
        repository.save(person);
        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());
        assertNotNull(retreivedPerson.get().getDrivingLicense());
        assertEquals(retreivedPerson.get().getDrivingLicense().getNumber(), "12345");
        assertTrue(retreivedPerson.get().getDrivingLicense() instanceof LazyLoadingProxy);
    }

    @Test
    public void shouldReplaceOneToOneRelation() {
        Passport passport = new Passport();
        passport.setNumber("12345");
        passportRepository.save(passport);
        Passport passport1 = new Passport();
        passport1.setNumber("77777");
        passportRepository.save(passport1);
        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setPassport(passport);
        repository.save(person);
        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());
        assertEquals(retreivedPerson.get().getPassport().getNumber(), "12345");

        person.setPassport(passport1);
        repository.save(person);
        Optional<Person> retreivedPerson1 = repository.findById(person.getId().toString());
        assertEquals(retreivedPerson1.get().getPassport().getNumber(), "77777");
    }

    @Test
    public void shouldReplaceOneToManyRelation() {
        Car car = new Car(1);
        car.setColor(Color.BLUE);
        String manufacturer = "BMW";
        car.setManufacturer(manufacturer);
        carRepository.save(car);

        Car car1 = new Car(2);
        car1.setColor(Color.RED);
        String manufacturer1 = "JAGUAR";
        car1.setManufacturer(manufacturer1);
        carRepository.save(car1);

        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setCars(Arrays.asList(new Car[] { car }));
        repository.save(person);
        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());
        assertFalse(retreivedPerson.get().getCars().isEmpty());
        assertTrue(retreivedPerson.get().getCars().get(0).getColor().equals(Color.BLUE));
        assertTrue(retreivedPerson.get().getCars().get(0).getManufacturer().equals(manufacturer));

        person.setCars(Arrays.asList(new Car[] { car1 }));
        repository.save(person);
        Optional<Person> retreivedPerson1 = repository.findById(person.getId().toString());
        assertFalse(retreivedPerson1.get().getCars().isEmpty());
        assertTrue(retreivedPerson1.get().getCars().get(0).getColor().equals(Color.RED));
        assertTrue(retreivedPerson1.get().getCars().get(0).getManufacturer().equals(manufacturer1));
    }

    @Test
    public void shouldRemoveOneToOneRelation() {
        Passport passport = new Passport();
        passport.setNumber("12345");
        passportRepository.save(passport);

        DrivingLicense drivingLicense = new DrivingLicense("OK9223");
        drivingLicense.setNumber("12345");
        drivingLicenseRepository.save(drivingLicense);

        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setPassport(passport);
        person.setDrivingLicense(drivingLicense);
        repository.save(person);

        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());
        assertEquals(retreivedPerson.get().getPassport().getNumber(), "12345");

        person.setPassport(null);
        person.setDrivingLicense(null);
        repository.save(person);
        Optional<Person> retreivedPerson1 = repository.findById(person.getId().toString());
        assertNull(retreivedPerson1.get().getPassport());
        assertNull(retreivedPerson1.get().getDrivingLicense());
    }

    @Test
    public void shouldRemoveElementFromOneToManyRelation() {
        Car car = new Car(1);
        car.setColor(Color.BLUE);
        String manufacturer = "BMW";
        car.setManufacturer(manufacturer);
        carRepository.save(car);

        Car car1 = new Car(2);
        car1.setColor(Color.RED);
        String manufacturer1 = "JAGUAR";
        car1.setManufacturer(manufacturer1);
        carRepository.save(car1);

        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setCars(Arrays.asList(new Car[] { car, car1 }));
        repository.save(person);

        Optional<Person> retreivedPerson = repository.findById(person.getId().toString());

        assertTrue(retreivedPerson.get().getCars().size() == 2);
        assertTrue(retreivedPerson.get().getCars().get(0).getColor().equals(Color.BLUE));
        assertTrue(retreivedPerson.get().getCars().get(0).getManufacturer().equals(manufacturer));

        retreivedPerson.get().getCars().remove(0);
        repository.save(retreivedPerson.get());
        Optional<Person> retreivedPerson1 = repository.findById(person.getId().toString());
        assertTrue(retreivedPerson.get().getCars().size() == 1);
        assertTrue(retreivedPerson1.get().getCars().get(0).getColor().equals(Color.RED));
        assertTrue(retreivedPerson1.get().getCars().get(0).getManufacturer().equals(manufacturer1));
    }

    @Test
    public void shouldFetchAggreation() {
        Car car1 = new Car(1);
        car1.setColor(Color.BLUE);
        car1.setManufacturer("BMW");
        Car car2 = new Car(2);
        car2.setColor(Color.RED);
        car2.setManufacturer("JAGUAR");
        carRepository.save(car1);
        carRepository.save(car2);
        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");
        person.setCars(Arrays.asList(new Car[] { car1, car2 }));
        repository.save(person);

        List<Person> findAll = repository.findAll();
        assertTrue(findAll.get(0).getCars().size() == 2);
        assertEquals("JAGUAR", findAll.get(0).getCars().get(1).getManufacturer());
    }

    @Test
    public void shouldNotLazyLoadAggregation() {
        House house = new House("H5");
        house.setAddress("Paris");

        House house1 = new House("H7");
        house.setAddress("Bir El Hafey");

        Person person = new Person();
        person.setName("Dave");
        person.setEmail("dave@mail.com");

        person.setHouses(Arrays.asList(new House[] { house, house1 }));
        repository.save(person);
        List<Person> findAll = repository.findAll();
        assertTrue(findAll.get(0) != null);
        assertTrue(findAll.get(0).getHouses().size() == 2);
        assertFalse(findAll.get(0).getHouses() instanceof LazyLoadingProxy);
    }

}
