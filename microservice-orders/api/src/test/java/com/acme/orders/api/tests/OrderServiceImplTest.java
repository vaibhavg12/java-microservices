package com.microservice.orders.api.tests;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

import com.microservice.orders.api.integrations.CatalogueClient;
import com.microservice.orders.api.integrations.CustomersClient;
import com.microservice.orders.api.models.OrderDAO;
import com.microservice.orders.api.models.db.OrderEntity;
import com.microservice.orders.api.models.db.OrderItemEntity;
import com.microservice.orders.api.services.impl.OrderServiceImpl;
import com.microservice.orders.lib.v1.Order;
import com.microservice.orders.lib.v1.OrderStatus;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class OrderServiceImplTest {

    @InjectMocks
    private OrderServiceImpl orderService;

    @Mock
    private OrderDAO orderDAOMock;

    @Mock
    private CustomersClient customersClientMock;

    @Mock
    private CatalogueClient catalogueClientMock;

    @Mock
    private MetricRegistry metricRegistryMock;

    @Mock
    private Meter meterMock;

    @Mock
    private Counter counterMock;

    @Mock
    private Histogram histogramMock;

    @Before
    public void setup() {

        Date date = Date.from(Instant.now());

        OrderItemEntity orderItemEntity = new OrderItemEntity();
        orderItemEntity.setProductId("1234");
        orderItemEntity.setQuantity(new BigDecimal("1"));

        Set<OrderItemEntity> orderItemEntities = new HashSet<>();
        orderItemEntities.add(orderItemEntity);

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setId("9f099726-b0eb-4d1c-9459-76b7a8ac96f4");
        orderEntity.setUpdatedAt(date);
        orderEntity.setCreatedAt(date);
        orderEntity.setStatus(OrderStatus.NEW);
        orderEntity.setCustomerId("4b1d5a5a-ae54-4816-b5c4-3bd5c98aa8f8");
        orderEntity.setCart(orderItemEntities);

        when(orderDAOMock.findById(eq("9f099726-b0eb-4d1c-9459-76b7a8ac96f4"))).thenReturn(orderEntity);
    }

    @After
    public void tearDown(){

        reset(orderDAOMock);
    }

    @Test
    public void testFindOrderById() {

        Order order = orderService.findOrderById("9f099726-b0eb-4d1c-9459-76b7a8ac96f4");

        verify(orderDAOMock).findById("9f099726-b0eb-4d1c-9459-76b7a8ac96f4");

        assertThat("Result is not null.", order, is(not(nullValue())));
        assertThat("Correct order ID.", order.getId(), is("9f099726-b0eb-4d1c-9459-76b7a8ac96f4"));
    }
}
