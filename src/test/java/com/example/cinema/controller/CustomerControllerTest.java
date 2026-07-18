package com.example.cinema.controller;

import com.example.cinema.config.GlobalErrorHandler;
import com.example.cinema.domain.Customer;
import com.example.cinema.dto.CustomerResponse;
import com.example.cinema.exception.CustomerNotFoundException;
import com.example.cinema.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CustomerController(customerService))
                .setControllerAdvice(new GlobalErrorHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getAllCustomers_withExistingCustomers_returnsCustomers() throws Exception {
        CustomerResponse customer = customerResponseDTO(1L, "123 Main Street", Customer.Gender.FEMALE);
        when(customerService.findAllResponseDTO()).thenReturn(List.of(customer));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerId").value(1L))
                .andExpect(jsonPath("$[0].userId").value(10L))
                .andExpect(jsonPath("$[0].email").value("customer@example.com"))
                .andExpect(jsonPath("$[0].fullName").value("Customer Name"))
                .andExpect(jsonPath("$[0].address").value("123 Main Street"))
                .andExpect(jsonPath("$[0].gender").value("FEMALE"))
                .andExpect(jsonPath("$[0].user").doesNotExist());
    }

    @Test
    void getCustomerById_withExistingCustomer_returnsCustomer() throws Exception {
        CustomerResponse customer = customerResponseDTO(1L, "123 Main Street", Customer.Gender.FEMALE);
        when(customerService.getResponseDTOById(1L)).thenReturn(customer);

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1L))
                .andExpect(jsonPath("$.userId").value(10L))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void getCustomerById_withMissingCustomer_returnsNotFound() throws Exception {
        when(customerService.getResponseDTOById(404L)).thenThrow(new CustomerNotFoundException(404L));

        mockMvc.perform(get("/api/customers/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createCustomer_withValidRequest_returnsCreatedCustomer() throws Exception {
        Customer saved = customer(1L, "123 Main Street", Customer.Gender.FEMALE);
        when(customerService.create(any(Customer.class))).thenReturn(saved);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1L));
    }

    @Test
    void updateCustomer_withExistingCustomer_returnsUpdatedCustomer() throws Exception {
        Customer updated = customer(1L, "456 Oak Avenue", Customer.Gender.MALE);
        when(customerService.update(any(Long.class), any(Customer.class))).thenReturn(updated);

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("456 Oak Avenue"));
    }

    @Test
    void updateCustomer_withMissingCustomer_returnsNotFound() throws Exception {
        Customer updated = customer(null, "456 Oak Avenue", Customer.Gender.MALE);
        when(customerService.update(any(Long.class), any(Customer.class)))
                .thenThrow(new CustomerNotFoundException(404L));

        mockMvc.perform(put("/api/customers/404")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCustomer_withExistingCustomer_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Customer deleted successfully"));

        verify(customerService).delete(1L);
    }

    private Customer customer(Long id, String address, Customer.Gender gender) {
        Customer customer = new Customer();
        customer.setCustomerId(id);
        customer.setAddress(address);
        customer.setGender(gender);
        return customer;
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
