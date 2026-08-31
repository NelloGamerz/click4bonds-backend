package com.click4bonds.app.Modules.ContactUS.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryRequest;
import com.click4bonds.app.Modules.ContactUS.Dto.ContactInquiryResponse;
import com.click4bonds.app.Modules.ContactUS.Service.ContactInquiryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/contact-inquiries")
@RequiredArgsConstructor
public class ContactInquiryController {

    private final ContactInquiryService contactInquiryService;

    @PostMapping
    public ResponseEntity<ContactInquiryResponse> createInquiry(
            @Valid @RequestBody ContactInquiryRequest request,
            HttpServletRequest httpRequest) {

        ContactInquiryResponse response = contactInquiryService.createInquiry(
                request,
                httpRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
