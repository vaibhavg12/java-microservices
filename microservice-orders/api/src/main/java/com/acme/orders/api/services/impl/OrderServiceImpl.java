package com.microservice.orders.api.services.impl;

import com.microservice.orders.api.integrations.CatalogueClient;
import com.microservice.orders.api.integrations.CustomersClient;
import com.microservice.orders.api.integrations.PaymentsClient;
import com.microservice.orders.api.integrations.lib.catalogue.Product;
import com.microservice.orders.api.mapper.OrderMapper;
import com.microservice.orders.api.models.OrderDAO;
import com.microservice.orders.api.models.db.OrderEntity;
import com.microservice.orders.api.models.db.OrderItemEntity;
import com.microservice.orders.api.rest.v1.auth.User;
import com.microservice.orders.api.services.OrderService;
import com.microservice.orders.api.services.exceptions.EmptyPayloadException;
import com.microservice.orders.api.services.exceptions.OrderServiceException;
import com.microservice.orders.api.services.exceptions.ResourceNotFoundException;
import com.microservice.orders.lib.v1.Order;
import com.microservice.orders.lib.v1.OrderStatus;
import com.microservice.orders.lib.v1.common.OrderServiceErrorCode;
import com.microservice.payments.lib.Transaction;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class OrderServiceImpl implements OrderService {

    private OrderDAO orderDAO;

    private CustomersClient customersClient;

    private CatalogueClient catalogueClient;

    private PaymentsClient paymentsClient;

    private final Meter createMeter;
    private final Meter completeMeter;
    private final Meter cancelMeter;

    private final Counter processingCounter;

    private final Histogram productsHistogram;

    public OrderServiceImpl(OrderDAO orderDAO, CustomersClient customersClient, CatalogueClient catalogueClient, PaymentsClient paymentsClient, MetricRegistry metricRegistry) {
        this.orderDAO = orderDAO;
        this.customersClient = customersClient;
        this.catalogueClient = catalogueClient;
        this.paymentsClient = paymentsClient;

        this.createMeter = metricRegistry.meter(OrderServiceImpl.class.getName() + ".orders-created");
        this.completeMeter = metricRegistry.meter(OrderServiceImpl.class.getName() + ".orders-completed");
        this.cancelMeter = metricRegistry.meter(OrderServiceImpl.class.getName() + ".orders-canceled");
        this.processingCounter = metricRegistry.counter(OrderServiceImpl.class.getName() + ".orders-processing");
        this.productsHistogram = metricRegistry.histogram(OrderServiceImpl.class.getName() + ".order-products");
    }

    @Override
    public Order findOrderById(String id) {

        OrderEntity orderEntity = orderDAO.findById(id);

        if (orderEntity == null) {
            throw new ResourceNotFoundException(Order.class.getSimpleName(), id);
        }

        return OrderMapper.toOrder(orderEntity);
    }

    @Override
    public List<Order> findOrders(Integer limit, Integer offset) {

        List<OrderEntity> orderEntities = orderDAO.findAll(limit, offset);

        return orderEntities.stream().map(OrderMapper::toOrder).collect(Collectors.toList());
    }

    @Override
    public Long findOrdersCount() {
        return orderDAO.findAllCount();
    }

    @Override
    public Order createOrder(Order order, User user) {

        if (order == null) {
            throw new EmptyPayloadException(Order.class.getSimpleName());
        }

        if (order.getCustomerId() != null) {

            customersClient.findCustomerById(order.getCustomerId(), user);
        }

        if (order.getCart() == null || order.getCart().isEmpty()) {
            throw new OrderServiceException(OrderServiceErrorCode.ORDER_CART_EMPTY);
        }

        Date date = Date.from(Instant.now());

        OrderEntity orderEntity = OrderMapper.toOrderEntity(order);
        orderEntity.setId(null);
        orderEntity.setUpdatedAt(date);
        orderEntity.setCreatedAt(date);
        orderEntity.setStatus(OrderStatus.NEW);

        for (OrderItemEntity orderItemEntity : orderEntity.getCart()) {

            Product product = catalogueClient.findProductById(orderItemEntity.getProductId());

            orderItemEntity.setTitle(product.getTitle());
            orderItemEntity.setCurrency(product.getCurrency());
            orderItemEntity.setPrice(product.getPrice());

            BigDecimal quantity = orderItemEntity.getQuantity() != null ? orderItemEntity.getQuantity() : BigDecimal.ONE;

            orderItemEntity.setQuantity(quantity);
            orderItemEntity.setAmount(product.getPrice().multiply(quantity));
        }

        orderDAO.create(orderEntity);

        // Metrics
        createMeter.mark();

        productsHistogram.update(orderEntity.getCart().size());

        return OrderMapper.toOrder(orderEntity);
    }

    @Override
    public Order completeOrder(String id) {

        OrderEntity orderEntity = orderDAO.findById(id);

        if (orderEntity == null) {
            throw new ResourceNotFoundException(Order.class.getSimpleName(), id);
        }

        if (!orderEntity.getStatus().equals(OrderStatus.NEW)) {
            throw new OrderServiceException(OrderServiceErrorCode.ORDER_STATE_INCORRECT);
        }

        processingCounter.inc();

        Transaction transaction = paymentsClient.createTransaction(orderEntity);

        orderEntity.setTransactionId(transaction.getId());
        orderEntity.setStatus(OrderStatus.COMPLETED);

        processingCounter.dec();
        completeMeter.mark();

        return OrderMapper.toOrder(orderEntity);
    }

    @Override
    public Order cancelOrder(String id) {

        OrderEntity orderEntity = orderDAO.findById(id);

        if (orderEntity == null) {
            throw new ResourceNotFoundException(Order.class.getSimpleName(), id);
        }

        if (!orderEntity.getStatus().equals(OrderStatus.NEW)) {
            throw new OrderServiceException(OrderServiceErrorCode.ORDER_STATE_INCORRECT);
        }

        orderEntity.setStatus(OrderStatus.CANCELED);

        cancelMeter.mark();

        return OrderMapper.toOrder(orderEntity);
    }
}
