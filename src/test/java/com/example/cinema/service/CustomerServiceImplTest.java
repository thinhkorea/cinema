package com.example.cinema.service;

import com.example.cinema.constant.AppConstants;
import com.example.cinema.domain.Customer;
import com.example.cinema.domain.User;
import com.example.cinema.dto.CustomerResponse;
import com.example.cinema.exception.CustomerNotFoundException;
import com.example.cinema.mapper.CustomerMapper;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerServiceImpl customerService;

    @Test
    void findAll_withExistingCustomers_returnsCustomers() {
        Customer customer = new Customer();
        when(customerRepo.findAll()).thenReturn(List.of(customer));

        List<Customer> result = customerService.findAll();

        assertThat(result).containsExactly(customer);
        verify(customerRepo).findAll();
    }

    @Test
    void findAllResponseDTO_withExistingCustomers_returnsCustomerResponseDTOs() {
        Customer customer = new Customer();
        CustomerResponse responseDTO = customerResponseDTO(1L, "123 Main Street", Customer.Gender.FEMALE);

        when(customerRepo.findAll()).thenReturn(List.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(responseDTO);

        List<CustomerResponse> result = customerService.findAllResponseDTO();

        assertThat(result).containsExactly(responseDTO);
        verify(customerRepo).findAll();
        verify(customerMapper).toResponse(customer);
    }

    @Test
    void findById_withExistingCustomer_returnsCustomer() {
        Customer customer = new Customer();
        when(customerRepo.findById(1L)).thenReturn(Optional.of(customer));

        Optional<Customer> result = customerService.findById(1L);

        assertThat(result).contains(customer);
        verify(customerRepo).findById(1L);
    }

    @Test
    void getById_withExistingCustomer_returnsCustomer() {
        Customer customer = new Customer();
        when(customerRepo.findById(1L)).thenReturn(Optional.of(customer));

        Customer result = customerService.getById(1L);

        assertThat(result).isSameAs(customer);
        verify(customerRepo).findById(1L);
    }

    @Test
    void getById_withMissingCustomer_throwsCustomerNotFoundException() {
        when(customerRepo.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.getById(404L))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage(AppConstants.CUSTOMER_NOT_FOUND + ": 404");
    }

    @Test
    void getResponseDTOById_withExistingCustomer_returnsCustomerResponseDTO() {
        Customer customer = new Customer();
        CustomerResponse responseDTO = customerResponseDTO(1L, "123 Main Street", Customer.Gender.FEMALE);

        when(customerRepo.findById(1L)).thenReturn(Optional.of(customer));
        when(customerMapper.toResponse(customer)).thenReturn(responseDTO);

        CustomerResponse result = customerService.getResponseDTOById(1L);

        assertThat(result).isSameAs(responseDTO);
        verify(customerRepo).findById(1L);
        verify(customerMapper).toResponse(customer);
    }

    @Test
    void create_withExistingUserId_returnsSavedCustomer() {
        User requestUser = new User();
        requestUser.setUserId(10L);

        User persistedUser = new User();
        persistedUser.setUserId(10L);
        persistedUser.setEmail("customer@example.com");

        Customer customer = new Customer();
        customer.setUser(requestUser);

        when(userRepo.findById(10L)).thenReturn(Optional.of(persistedUser));
        when(customerRepo.save(customer)).thenReturn(customer);

        Customer result = customerService.create(customer);

        assertThat(result).isSameAs(customer);
        assertThat(customer.getUser()).isSameAs(persistedUser);
        verify(userRepo).findById(10L);
        verify(customerRepo).save(customer);
    }

    @Test
    void create_withoutUserId_returnsSavedCustomer() {
        Customer customer = new Customer();
        when(customerRepo.save(customer)).thenReturn(customer);

        Customer result = customerService.create(customer);

        assertThat(result).isSameAs(customer);
        verifyNoInteractions(userRepo);
        verify(customerRepo).save(customer);
    }

    @Test
    void create_withUserWithoutUserId_returnsSavedCustomer() {
        User requestUser = new User();

        Customer customer = new Customer();
        customer.setUser(requestUser);

        when(customerRepo.save(customer)).thenReturn(customer);

        Customer result = customerService.create(customer);

        assertThat(result).isSameAs(customer);
        assertThat(customer.getUser()).isSameAs(requestUser);
        verifyNoInteractions(userRepo);
        verify(customerRepo).save(customer);
    }

    @Test
    void create_withMissingUser_throwsIllegalArgumentException() {
        User requestUser = new User();
        requestUser.setUserId(10L);

        Customer customer = new Customer();
        customer.setUser(requestUser);

        when(userRepo.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.create(customer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AppConstants.USER_NOT_FOUND);
    }

    @Test
    void update_withExistingCustomer_returnsUpdatedCustomer() {
        Customer existing = new Customer();
        existing.setCustomerId(1L);
        existing.setAddress("Old address");
        existing.setGender(Customer.Gender.MALE);
        existing.setLoyaltyPoints(50);

        Customer updated = new Customer();
        updated.setAddress("New address");
        updated.setGender(Customer.Gender.FEMALE);
        updated.setLoyaltyPoints(999);

        when(customerRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepo.save(existing)).thenReturn(existing);

        Customer result = customerService.update(1L, updated);

        assertThat(result).isSameAs(existing);
        assertThat(existing.getAddress()).isEqualTo("New address");
        assertThat(existing.getGender()).isEqualTo(Customer.Gender.FEMALE);
        assertThat(existing.getLoyaltyPoints()).isEqualTo(50);
        verify(customerRepo).save(existing);
    }

    @Test
    void update_withMissingCustomer_throwsCustomerNotFoundException() {
        when(customerRepo.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(404L, new Customer()))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessage(AppConstants.CUSTOMER_NOT_FOUND + ": 404");
    }

    @Test
    void delete_withExistingCustomerId_deletesCustomer() {
        customerService.delete(1L);

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(customerRepo).deleteById(idCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(1L);
    }

    private CustomerResponse customerResponseDTO(Long id, String address, Customer.Gender gender) {
        return new CustomerResponse(
                id,
                10L,
                "customer@example.com",
                "0900000000",
                "Customer Name",
                address,
                gender,
                50
        );
    }
}
