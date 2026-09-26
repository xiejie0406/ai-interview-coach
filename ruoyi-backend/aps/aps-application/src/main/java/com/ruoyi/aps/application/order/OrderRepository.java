package com.ruoyi.aps.application.order;

import java.util.List;
import java.util.Optional;
import com.ruoyi.aps.application.order.OrderCatalog.MaterialDemand;
import com.ruoyi.aps.application.order.OrderCatalog.OrderLine;
import com.ruoyi.aps.application.order.OrderCatalog.ProductionLot;
import com.ruoyi.aps.application.order.OrderCatalog.ProductionOrder;
import com.ruoyi.aps.application.order.OrderCatalog.Task;
import com.ruoyi.aps.application.order.OrderCatalog.TaskDependency;

public interface OrderRepository
{
    List<ProductionOrder> listOrders();
    Optional<ProductionOrder> findOrder(String id);
    Optional<ProductionOrder> findByExternalId(String sourceSystem, String externalId);
    Optional<OrderLine> findOrderLine(String id);
    void insertOrder(ProductionOrder order, String actor);
    void insertOrderLine(OrderLine line, String actor);
    int updateOrderStatus(String id, long rowVersion, String status, String actor);
    void releaseOrderChildren(String id, String actor);
    int updateOrderLineRoute(String id, String expectedRouteVersionId, String targetRouteVersionId,
            long rowVersion, String actor);

    List<ProductionLot> listLots(String orderId);
    Optional<ProductionLot> findLot(String id);
    void insertLot(ProductionLot lot, String actor);
    int updateLotQuantity(String id, long rowVersion, java.math.BigDecimal quantity, String actor);
    List<Task> listTasks(String orderId);
    default Optional<Task> findTask(String taskId) { return Optional.empty(); }
    List<Task> listTasksByLot(String lotId);
    List<Task> listTasksByLine(String orderLineId);
    void insertTask(Task task, String actor);
    int updateTaskQuantity(Task task, long rowVersion, String actor);
    List<TaskDependency> listDependencies(String orderId);
    void insertDependency(TaskDependency dependency, String actor);
    List<MaterialDemand> listMaterialDemands(String orderId);
    void insertMaterialDemand(MaterialDemand demand, String actor);

    /** 按 M25～M29 当前有效事实重算 M16→M15→M14→M13 状态投影。 */
    default void recomputeExecutionStatuses(String taskId, String actor) { }
}
