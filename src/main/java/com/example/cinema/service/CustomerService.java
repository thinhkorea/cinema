package com.example.cinema.service;

import com.example.cinema.domain.Customer;
import com.example.cinema.dto.CustomerResponse;
import com.example.cinema.exception.CustomerNotFoundException;

import java.util.List;
import java.util.Optional;

public interface CustomerService {

    /**
     * Retrieves all customers.
     *
     * @return all customers currently stored in the system
     */
    List<Customer> findAll();

    /**
     * Retrieves all customers as response DTOs.
     *
     * @return all customer response DTOs currently stored in the system
     */
    List<CustomerResponse> findAllResponseDTO();

    /**
     * Retrieves a customer by id.
     *
     * @param id customer id to search for
     * @return the matching customer, or {@link Optional#empty()} when no customer exists
     */
    Optional<Customer> findById(Long id);

    /**
     * Retrieves a customer by id.
     *
     * @param id customer id to search for
     * @return the matching customer
     * @throws CustomerNotFoundException when the customer id does not exist
     */
    Customer getById(Long id);

    /**
     * Retrieves a customer response DTO by id.
     *
     * @param id customer id to search for
     * @return the matching customer response DTO
     * @throws CustomerNotFoundException when the customer id does not exist
     */
    CustomerResponse getResponseDTOById(Long id);

    /**
     * Creates a customer. When the request includes a user id, the customer is linked to the persisted user.
     *
     * @param customer customer data to create
     * @return the saved customer
     * @throws IllegalArgumentException when the referenced user id does not exist
     */
    Customer create(Customer customer);

    /**
     * Updates editable customer profile fields.
     *
     * @param id customer id to update
     * @param updated updated customer data
     * @return the saved customer with updated fields
     * @throws CustomerNotFoundException when the customer id does not exist
     */
    Customer update(Long id, Customer updated);

    /**
     * Deletes a customer by id.
     *
     * @param id customer id to delete
     */
    void delete(Long id);
}
