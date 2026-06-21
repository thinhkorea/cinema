package com.example.cinema.service;

import com.example.cinema.constant.AppConstants;
import com.example.cinema.domain.Customer;
import com.example.cinema.domain.User;
import com.example.cinema.dto.CustomerResponse;
import com.example.cinema.exception.CustomerNotFoundException;
import com.example.cinema.mapper.CustomerMapper;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final CustomerMapper customerMapper;

    @Transactional(readOnly = true)
    @Override
    public List<Customer> findAll() {
        return customerRepo.findAll();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CustomerResponse> findAllResponseDTO() {
        return customerRepo.findAll().stream()
                .map(customerMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<Customer> findById(Long id) {
        return customerRepo.findById(id);
    }

    @Transactional(readOnly = true)
    @Override
    public Customer getById(Long id) {
        return customerRepo.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerResponse getResponseDTOById(Long id) {
        return customerMapper.toResponse(getById(id));
    }

    @Transactional
    @Override
    public Customer create(Customer customer) {
        if (customer.getUser() != null && customer.getUser().getUserId() != null) {
            User user = userRepo.findById(customer.getUser().getUserId())
                    .orElseThrow(() -> new IllegalArgumentException(AppConstants.USER_NOT_FOUND));
            customer.setUser(user);
        }

        return customerRepo.save(customer);
    }

    @Transactional
    @Override
    public Customer update(Long id, Customer updated) {
        Customer existing = getById(id);
        existing.setGender(updated.getGender());
        existing.setAddress(updated.getAddress());
        return customerRepo.save(existing);
    }

    @Transactional
    @Override
    public void delete(Long id) {
        customerRepo.deleteById(id);
    }
}
