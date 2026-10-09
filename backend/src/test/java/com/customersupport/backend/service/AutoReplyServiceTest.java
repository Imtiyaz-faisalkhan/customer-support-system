package com.customersupport.backend.service;

import com.customersupport.backend.dto.KnowledgeBaseAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoReplyServiceTest {

    @Mock
    private TicketService ticketService;

    @Mock
    private KnowledgeBaseResponder responder;

    private AutoReplyService service;

    @BeforeEach
    void setUp() {
        // Runs the "background" work on the test thread, so it's done when replyAsync returns.
        service = new AutoReplyService(ticketService, responder, Runnable::run);
    }

    @Test
    void answerableFromTheKnowledgeBase_sendsTheAiReply() {
        when(responder.answer("Login", "I can't log in"))
                .thenReturn(Optional.of(new KnowledgeBaseAnswer(true, "Try Forgot Password first.")));
        when(ticketService.sendAiReply(7L, "Try Forgot Password first.")).thenReturn(true);

        service.replyAsync(7L, "Login", "I can't log in");

        verify(ticketService, never()).escalateToHuman(7L);
    }

    @Test
    void notAnswerable_escalatesToTheAdmin() {
        when(responder.answer("Shipping", "Do you ship to Canada?"))
                .thenReturn(Optional.of(KnowledgeBaseAnswer.notAnswerable()));

        service.replyAsync(7L, "Shipping", "Do you ship to Canada?");

        verify(ticketService).escalateToHuman(7L);
        verify(ticketService, never()).sendAiReply(anyLong(), anyString());
    }

    @Test
    void answerableButBlankReply_escalates() {
        when(responder.answer("Login", "Help")).thenReturn(Optional.of(new KnowledgeBaseAnswer(true, " ")));

        service.replyAsync(7L, "Login", "Help");

        verify(ticketService).escalateToHuman(7L);
    }

    @Test
    void aiReplySkipped_escalates() {
        when(responder.answer("Login", "I can't log in"))
                .thenReturn(Optional.of(new KnowledgeBaseAnswer(true, "Try Forgot Password first.")));
        when(ticketService.sendAiReply(7L, "Try Forgot Password first.")).thenReturn(false);

        service.replyAsync(7L, "Login", "I can't log in");

        verify(ticketService).escalateToHuman(7L);
    }

    @Test
    void replyThatCannotBeSent_escalates() {
        when(responder.answer("Login", "I can't log in"))
                .thenReturn(Optional.of(new KnowledgeBaseAnswer(true, "Try Forgot Password first.")));
        when(ticketService.sendAiReply(7L, "Try Forgot Password first."))
                .thenThrow(new MailSendException("SMTP down"));

        service.replyAsync(7L, "Login", "I can't log in");

        verify(ticketService).escalateToHuman(7L);
    }

    @Test
    void aiOff_leavesTheTicketAlone() {
        when(responder.answer("Login", "I can't log in")).thenReturn(Optional.empty());

        service.replyAsync(7L, "Login", "I can't log in");

        verifyNoInteractions(ticketService);
    }
}
