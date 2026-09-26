package com.ruoyi.fashion.application.selection.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.ruoyi.fashion.application.selection.SelectionComboView;
import com.ruoyi.fashion.application.selection.SelectionComboWrite;
import com.ruoyi.fashion.application.selection.SelectionDetailWrite;
import com.ruoyi.fashion.application.selection.SelectionProductFact;

public interface FashionSelectionRepository {
    List<SelectionProductFact> findCandidateFacts(Collection<String> categories, String warehouseCode);

    List<SelectionComboView> findByQuoteId(long quoteId);

    Optional<SelectionComboView> findCombo(long quoteId, long comboId);

    void deselectCurrent(long quoteId, long operatorId, Instant now);

    boolean deselectCombo(long comboId, long expectedRowVersion, long operatorId, Instant now);

    void insertCombo(SelectionComboWrite combo, List<SelectionDetailWrite> details);

    boolean updateLocks(long comboId, List<String> lockedSlots, long expectedRowVersion, long operatorId, Instant now);
}
