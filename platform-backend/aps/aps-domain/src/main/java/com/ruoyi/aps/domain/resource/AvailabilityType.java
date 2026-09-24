package com.ruoyi.aps.domain.resource;

public enum AvailabilityType
{
    AVAILABLE(true),
    OVERTIME(true),
    UNAVAILABLE(false),
    LEAVE(false),
    MAINTENANCE(false);

    private final boolean productive;

    AvailabilityType(boolean productive)
    {
        this.productive = productive;
    }

    public boolean productive()
    {
        return productive;
    }
}
