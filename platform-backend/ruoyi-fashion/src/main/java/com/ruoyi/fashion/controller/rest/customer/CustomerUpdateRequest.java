package com.ruoyi.fashion.controller.rest.customer;

import java.util.List;

import com.ruoyi.fashion.application.customer.CustomerUpdate;

public class CustomerUpdateRequest {
    public String name;
    public String customerType;
    public String contactName;
    public String contactPhone;
    public String region;
    public Long salespersonId;
    public List<Long> collaboratorIds;
    public String internalNote;
    public long rowVersion;

    CustomerUpdate toCommand() {
        return new CustomerUpdate(name, customerType, contactName, contactPhone, region,
                salespersonId, collaboratorIds, internalNote, rowVersion);
    }
}
