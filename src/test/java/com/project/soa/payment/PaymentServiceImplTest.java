package com.project.soa.payment;

import com.project.soa.booking.Booking;
import com.project.soa.booking.BookingStatus;
import com.project.soa.booking.BookingRepository;
import com.project.soa.audit.AuditLogService;
import com.project.soa.common.exception.BusinessRuleException;
import com.project.soa.common.exception.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock BookingRepository bookingRepository;
    @Mock AuditLogService auditLogService;

    PaymentServiceImpl service;
    Clock fixedClock = Clock.fixed(Instant.parse("2025-06-01T10:00:00Z"), ZoneOffset.UTC);

    UUID bookingId = UUID.randomUUID();
    UUID paymentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, bookingRepository, auditLogService, fixedClock);
    }

    // ── createPaymentIntent ───────────────────────────────────────────────────

    @Test
    void createPaymentIntent_bookingFound_createsPaymentWithCorrectAmount() {
        Booking booking = booking(new BigDecimal("350.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(paymentId);
            return p;
        });

        Payment result = service.createPaymentIntent(intentDto(bookingId));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getAmount()).isEqualByComparingTo("350.00");
        assertThat(result.getBooking()).isEqualTo(booking);
    }

    @Test
    void createPaymentIntent_bookingNotFound_throwsResourceNotFound() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createPaymentIntent_nonPendingBooking_throwsBusinessRule() {
        Booking booking = booking(new BigDecimal("350.00"));
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PENDING bookings");
    }

    @Test
    void createPaymentIntent_cancelledBooking_throwsBusinessRule() {
        Booking booking = booking(new BigDecimal("350.00"));
        booking.setStatus(BookingStatus.CANCELLED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PENDING bookings");
    }

    @Test
    void createPaymentIntent_expiredStatusBooking_throwsBusinessRule() {
        Booking booking = booking(new BigDecimal("350.00"));
        booking.setStatus(BookingStatus.EXPIRED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PENDING bookings");
    }

    @Test
    void createPaymentIntent_expiredPendingBooking_throwsBusinessRule() {
        Booking booking = booking(new BigDecimal("350.00"));
        booking.setPendingExpiresAt(LocalDateTime.now(fixedClock).minusMinutes(1));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reservation expired");
    }

    @Test
    void createPaymentIntent_existingPendingPaymentIntent_throwsBusinessRule() {
        Booking booking = booking(new BigDecimal("350.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.PENDING)).thenReturn(Optional.of(payment(PaymentStatus.PENDING)));

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pending payment intent already exists");
    }

    @Test
    void createPaymentIntent_existingSuccessfulPayment_throwsBusinessRule() {
        Booking booking = booking(new BigDecimal("350.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.of(payment(PaymentStatus.SUCCESS)));

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already been paid");
    }

    @Test
    void createPaymentIntent_existingFailedPayment_createsNewPaymentIntent() {
        Booking booking = booking(new BigDecimal("350.00"));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.PENDING)).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = service.createPaymentIntent(intentDto(bookingId));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getAmount()).isEqualByComparingTo("350.00");
    }

    @Test
    void createPaymentIntent_existingFailedPaymentButBookingExpired_throwsBusinessRule() {
        Booking booking = booking(new BigDecimal("350.00"));
        booking.setPendingExpiresAt(LocalDateTime.now(fixedClock).minusMinutes(1));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.createPaymentIntent(intentDto(bookingId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reservation expired");
        verify(paymentRepository, never()).save(any());
    }

    // ── simulatePayment ───────────────────────────────────────────────────────

    @Test
    void simulatePayment_success_setsStatusToSuccess() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto result = service.simulatePayment(paymentId, true);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS.name());
    }

    @Test
    void simulatePayment_failure_setsStatusToFailed() {
        Payment payment = payment(PaymentStatus.PENDING);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto result = service.simulatePayment(paymentId, false);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED.name());
    }

    @Test
    void simulatePayment_notFound_throwsResourceNotFound() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simulatePayment(paymentId, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void simulatePayment_alreadySuccessful_throwsBusinessRule() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment(PaymentStatus.SUCCESS)));

        assertThatThrownBy(() -> service.simulatePayment(paymentId, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only PENDING payments");
    }

    @Test
    void simulatePayment_expiredBooking_throwsBusinessRule() {
        Payment payment = payment(PaymentStatus.PENDING);
        payment.getBooking().setPendingExpiresAt(LocalDateTime.now(fixedClock).minusMinutes(1));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.simulatePayment(paymentId, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reservation expired");
    }

    @Test
    void simulatePayment_amountMismatch_throwsBusinessRule() {
        Payment payment = payment(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("349.99"));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.simulatePayment(paymentId, true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("amount does not match");
    }

    // ── handleBookingCancellation ─────────────────────────────────────────────

    @Test
    void handleCancellation_noPenalty_setsRefunded() {
        Payment payment = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String status = service.handleBookingCancellation(bookingId, BigDecimal.ZERO);

        assertThat(status).isEqualTo(PaymentStatus.REFUNDED.name());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void handleCancellation_withPenalty_setsPartialRefund() {
        Payment payment = payment(PaymentStatus.SUCCESS);
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String status = service.handleBookingCancellation(bookingId, new BigDecimal("50.00"));

        assertThat(status).isEqualTo(PaymentStatus.PARTIAL_REFUND.name());
    }

    @Test
    void handleCancellation_paymentNotSuccess_doesNotChangeStatus() {
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());

        String status = service.handleBookingCancellation(bookingId, BigDecimal.ZERO);

        assertThat(status).isNull();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleCancellation_noPaymentRecord_returnsNull() {
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());

        assertThat(service.handleBookingCancellation(bookingId, BigDecimal.ZERO)).isNull();
    }

    // ── isBookingPaid ─────────────────────────────────────────────────────────

    @Test
    void isBookingPaid_successStatus_returnsTrue() {
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS)));

        assertThat(service.isBookingPaid(bookingId)).isTrue();
    }

    @Test
    void isBookingPaid_pendingStatus_returnsFalse() {
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());

        assertThat(service.isBookingPaid(bookingId)).isFalse();
    }

    @Test
    void isBookingPaid_noPayment_returnsFalse() {
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(
                bookingId, PaymentStatus.SUCCESS)).thenReturn(Optional.empty());

        assertThat(service.isBookingPaid(bookingId)).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Booking booking(BigDecimal totalPrice) {
        Booking b = new Booking();
        b.setId(bookingId);
        b.setTotalPrice(totalPrice);
        b.setStatus(BookingStatus.PENDING);
        b.setPendingExpiresAt(LocalDateTime.now(fixedClock).plusMinutes(15));
        return b;
    }

    private Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setId(paymentId);
        p.setStatus(status);
        p.setAmount(new BigDecimal("350.00"));
        p.setBooking(booking(new BigDecimal("350.00")));
        return p;
    }

    private CreatePaymentIntentRequestDto intentDto(UUID bId) {
        CreatePaymentIntentRequestDto dto = new CreatePaymentIntentRequestDto();
        dto.setBookingId(bId);
        return dto;
    }
}
