package com.customersupport.backend.service;

import com.customersupport.backend.dto.OutboundEmail;

/** Sends email to customers. The provider is still TBD; swap the implementation bean when it's chosen. */
public interface EmailSender {

    void send(OutboundEmail email);
}
