package com.ruoyi.fashion.controller.rest.customer;

import java.util.List;

import com.ruoyi.fashion.application.customer.CustomerCreate;

public class CustomerCreateRequest {
    public String code;
    public String name;
    public String customerType;
    public String contactName;
    public String contactPhone;
    public String region;
    public Long salespersonId;
    public List<Long> collaboratorIds;
    public String internalNote;

    CustomerCreate toCommand() {
        return new CustomerCreate(code, name, customerType, contactName, contactPhone, region,
                salespersonId, collaboratorIds, internalNote);
    }
}
