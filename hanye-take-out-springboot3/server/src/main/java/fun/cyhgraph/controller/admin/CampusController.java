package fun.cyhgraph.controller.admin;

import fun.cyhgraph.result.Result;
import fun.cyhgraph.service.CampusGraphService;
import fun.cyhgraph.vo.CampusGraphVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("adminCampusController")
@RequestMapping("/admin/campus")
public class CampusController {

    @Autowired
    private CampusGraphService campusGraphService;

    @GetMapping("/graph")
    public Result<CampusGraphVO> graph() {
        return Result.success(campusGraphService.getGraph());
    }

    @GetMapping("/graph/order/{orderId}")
    public Result<CampusGraphVO> graphByOrder(@PathVariable Integer orderId) {
        return Result.success(campusGraphService.getGraphByOrderId(orderId));
    }

    @GetMapping("/graph/rider/{riderId}")
    public Result<CampusGraphVO> graphByRider(@PathVariable Integer riderId) {
        return Result.success(campusGraphService.getGraphByRiderId(riderId));
    }
}
