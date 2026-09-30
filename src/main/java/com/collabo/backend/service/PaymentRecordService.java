package com.collabo.backend.service;

import com.collabo.backend.dto.PaymentDtos.PaymentResponse;
import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Payment records inside a space. Only a record-keeping feature: the platform never handles money, and a confirmed record
 * proves that both people said it happened, not that it did.
 */
@Service
@Transactional
public class PaymentRecordService {

    static final int MAX_NOTE = 200;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");
    private static final String GONE = "No such space.";

    private final SpaceRepository spaces;
    private final PaymentRecordRepository payments;
    private final SpaceMemberRepository members;
    private final UserRepository users;
    private final SpaceService spaceAccess;
    private final SpaceThreadService spaceThreads;

    public PaymentRecordService(SpaceRepository spaces, PaymentRecordRepository payments, SpaceMemberRepository members, UserRepository users,
                                SpaceService spaceAccess, SpaceThreadService spaceThreads) {
        this.spaces = spaces; this.payments = payments; this.members = members; this.users = users;
        this.spaceAccess = spaceAccess; this.spaceThreads = spaceThreads;
    }

    /** The payer fills the form: owner or LOG_PAYMENTS, to someone else in the room. */
    public PaymentResponse claim(User me, UUID spaceId, String recipientName, BigDecimal amount, String rawCurrency, String rawNote) {
        Space s = inRoom(me, spaceId);
        if (!spaceAccess.can(me, s, SpacePermission.LOG_PAYMENTS)) throw new ForbiddenException("You do not have permission to log payments here.");
        User recipient = users.findByUsername(recipientName == null ? "" : recipientName.trim())
                .orElseThrow(() -> new InvalidProfileException("Name someone in this room to pay."));
        boolean seated = members.findBySpaceIdAndUserId(s.getId(), recipient.getId()).filter(m -> m.getState() == SpaceMember.State.ACTIVE).isPresent();
        if (!seated || recipient.getId().equals(me.getId())) throw new InvalidProfileException("Name someone else who is in this room.");
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(MAX_AMOUNT) >= 0) {
            throw new InvalidProfileException("Enter an amount above zero with at most two decimals.");
        }
        String currency = rawCurrency == null ? "" : rawCurrency.trim().toUpperCase();
        if (!currency.matches("[A-Z]{3,5}")) throw new InvalidProfileException("Give the currency as 3 to 5 letters, like NGN.");
        String note = rawNote == null ? "" : rawNote.trim();
        if (note.length() > MAX_NOTE) throw new InvalidProfileException("Keep the note under " + MAX_NOTE + " characters.");

        PaymentRecord p = payments.save(new PaymentRecord(s.getId(), me.getId(), recipient.getId(), amount.setScale(2), currency, note));
        spaceThreads.announce(s, me.getUsername() + " logged a payment claim: " + p.getAmount() + " " + currency + " to " + recipient.getUsername()
                + ". Claimed, not yet confirmed. The room is held until it is confirmed or cancelled.");
        return one(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(User me, UUID spaceId) {
        Space s = inRoom(me, spaceId);
        List<PaymentRecord> rows = payments.findBySpaceIdOrderByCreatedAtDesc(s.getId());
        Set<UUID> ids = new HashSet<>();
        rows.forEach(p -> { ids.add(p.getPayerId()); ids.add(p.getRecipientId()); });
        Map<UUID, User> people = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(p -> present(p, people)).toList();
    }

    /** The recipient states it arrived. */
    public PaymentResponse confirm(User me, UUID spaceId, UUID paymentId) {
        Space s = inRoom(me, spaceId);
        PaymentRecord p = open(s, paymentId);
        if (!p.getRecipientId().equals(me.getId())) throw new ForbiddenException("Only the person who was paid can confirm it.");
        p.resolve(PaymentRecord.State.CONFIRMED);
        payments.save(p);
        spaceThreads.announce(s, me.getUsername() + " confirmed receiving " + p.getAmount() + " " + p.getCurrency() + " from " + name(p.getPayerId()) + ".");
        return one(p);
    }

    /** The payer withdraws the claim, so a silent recipient cannot hold the room. */
    public PaymentResponse cancel(User me, UUID spaceId, UUID paymentId) {
        Space s = inRoom(me, spaceId);
        PaymentRecord p = open(s, paymentId);
        if (!p.getPayerId().equals(me.getId())) throw new ForbiddenException("Only the person who logged the claim can cancel it.");
        p.resolve(PaymentRecord.State.CANCELLED);
        payments.save(p);
        spaceThreads.announce(s, me.getUsername() + " cancelled the payment claim of " + p.getAmount() + " " + p.getCurrency() + " to " + name(p.getRecipientId()) + ".");
        return one(p);
    }

    /** Payments are the team's business: owner and members only (an accepted applicant who has not joined sees nothing). */
    private Space inRoom(User me, UUID spaceId) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        String role = spaceAccess.roleOf(me, s);
        if (role == null || role.equals("APPLICANT")) throw new ResourceNotFoundException(GONE);
        return s;
    }

    private PaymentRecord open(Space s, UUID paymentId) {
        PaymentRecord p = payments.findByIdAndSpaceId(paymentId, s.getId()).orElseThrow(() -> new ResourceNotFoundException("No such payment record."));
        if (p.getState() != PaymentRecord.State.CLAIMED) throw new InvalidProfileException("This payment record is already settled.");
        return p;
    }

    private String name(UUID userId) { return users.findById(userId).map(User::getUsername).orElse("someone"); }

    private PaymentResponse one(PaymentRecord p) {
        return present(p, users.findAllById(List.of(p.getPayerId(), p.getRecipientId())).stream().collect(Collectors.toMap(User::getId, Function.identity())));
    }

    private static PaymentResponse present(PaymentRecord p, Map<UUID, User> people) {
        User payer = people.get(p.getPayerId()), recipient = people.get(p.getRecipientId());
        return new PaymentResponse(p.getId(), payer == null ? null : PersonDto.of(payer), recipient == null ? null : PersonDto.of(recipient),
                p.getAmount(), p.getCurrency(), p.getNote(), p.getState().name(), p.getCreatedAt(), p.getResolvedAt());
    }
}
