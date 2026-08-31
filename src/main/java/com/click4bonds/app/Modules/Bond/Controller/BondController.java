package com.click4bonds.app.Modules.Bond.Controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.click4bonds.app.Modules.Bond.Dto.BondResponse;
import com.click4bonds.app.Modules.Bond.Service.BondService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bonds")
@RequiredArgsConstructor
public class BondController {

    private final BondService bondService;

    @GetMapping
    public ResponseEntity<Page<BondResponse>> getBonds(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                bondService.getBonds(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BondResponse> getBond(
            @PathVariable String id) {

        return ResponseEntity.ok(
                bondService.getBond(id));
    }

    // @PostMapping("/{bondId}/calculate-ytm")
    // public YtmResponse calculateYtm(
    //         @PathVariable UUID bondId) {
    //     return bondYtmService.calculateYtm(bondId);
    // }
}
