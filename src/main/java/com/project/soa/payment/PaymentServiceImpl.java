package com.project.soa.payment;

import com.project.soa.booking.*;
import com.project.soa.audit.AuditLogService;
import com.project.soa.common.exception.BusinessRuleException;
import com.project.soa.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PaymentServiceImpl implements PaymentService, PaymentInternalService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;


    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              BookingRepository bookingRepository,
                              AuditLogService auditLogService,
                              Clock clock) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;

    }

    @Override
    public Payment createPaymentIntent(CreatePaymentIntentRequestDto dto) {

        Booking booking = bookingRepository.findById(dto.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", dto.getBookingId()));

        validateBookingPayable(booking);
        validateNoBlockingPayment(booking.getId());

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        payment.setStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.save(payment);
        auditLogService.logCurrentActor("PAYMENT_INTENT_CREATED", "Payment", saved.getId(),
                "Payment intent created for booking " + booking.getId());
        return saved;
    }

    @Override
    public PaymentResponseDto simulatePayment(UUID paymentId, boolean success) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException("Only PENDING payments can be simulated");
        }
        validateBookingPayable(payment.getBooking());
        if (payment.getAmount() == null || payment.getBooking().getTotalPrice() == null
                || payment.getAmount().compareTo(payment.getBooking().getTotalPrice()) != 0) {
            throw new BusinessRuleException("Payment amount does not match booking total price");
        }

        if (success) {
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentRepository.save(payment);



        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }

        auditLogService.logCurrentActor(
                success ? "PAYMENT_SIMULATED_SUCCESS" : "PAYMENT_SIMULATED_FAILURE",
                "Payment",
                payment.getId(),
                "Payment simulation completed with status " + payment.getStatus());
        return PaymentMapper.toDto(payment);
    }

    @Override
    public String handleBookingCancellation(UUID bookingId, BigDecimal penaltyAmount) {

        return paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, PaymentStatus.SUCCESS)
                .map(payment -> {
                    if (penaltyAmount.compareTo(BigDecimal.ZERO) == 0) {
                        payment.setStatus(PaymentStatus.REFUNDED);
                    } else {
                        payment.setStatus(PaymentStatus.PARTIAL_REFUND);
                    }

                    paymentRepository.save(payment);

                    return payment.getStatus().name();

                }).orElse(null);
    }

    @Override
    public boolean isBookingPaid(UUID bookingId) {
        return paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS).isPresent();
    }

    @Override
    public PaymentResponseDto getPaymentByBookingId(UUID bookingId) {

        Payment payment = paymentRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", bookingId));

        return PaymentMapper.toDto(payment);
    }

    @Override
    public PaymentResponseDto getPayment(UUID paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        return PaymentMapper.toDto(payment);
    }

    private void validateBookingPayable(Booking booking) {
        if (booking == null) {
            throw new BusinessRuleException("Payment must be linked to a booking");
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BusinessRuleException("Payment can only be created or processed for PENDING bookings");
        }
        if (booking.getPendingExpiresAt() != null
                && booking.getPendingExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Cannot process payment: booking reservation expired");
        }
        if (booking.getTotalPrice() == null) {
            throw new BusinessRuleException("Booking total price is required for payment");
        }
    }

    private void validateNoBlockingPayment(UUID bookingId) {
        if (paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.PENDING).isPresent()) {
            throw new BusinessRuleException("A pending payment intent already exists for this booking");
        }
        if (paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS).isPresent()) {
            throw new BusinessRuleException("Booking has already been paid");
        }
    }
}
