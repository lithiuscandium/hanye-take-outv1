"use strict";
const common_vendor = require("../../common/vendor.js");
const api_order = require("../../api/order.js");
require("../../utils/http.js");
require("../../stores/modules/user.js");
if (!Math) {
  CampusGraphCanvas();
}
const CampusGraphCanvas = () => "../../components/campus/CampusGraphCanvas.js";
const _sfc_main = /* @__PURE__ */ common_vendor.defineComponent({
  __name: "rider",
  setup(__props) {
    const graph = common_vendor.ref();
    const riderOptions = common_vendor.ref([]);
    const selectedRiderId = common_vendor.ref();
    const selectedRiderIndex = common_vendor.computed(() => {
      const idx = riderOptions.value.findIndex((item) => item.riderId === selectedRiderId.value);
      return idx >= 0 ? idx : 0;
    });
    const selectedRiderLabel = common_vendor.computed(() => {
      const current = riderOptions.value[selectedRiderIndex.value];
      return (current == null ? void 0 : current.label) || "暂无骑手数据";
    });
    const activeRider = common_vendor.computed(() => {
      var _a;
      return (((_a = graph.value) == null ? void 0 : _a.riders) || []).find((item) => item.riderId === selectedRiderId.value);
    });
    const currentNodeName = common_vendor.computed(() => {
      var _a, _b, _c;
      const nodeId = (_a = activeRider.value) == null ? void 0 : _a.currentNodeId;
      if (nodeId === void 0) {
        return "-";
      }
      return ((_c = (((_b = graph.value) == null ? void 0 : _b.nodes) || []).find((node) => node.nodeId === nodeId)) == null ? void 0 : _c.name) || `节点${nodeId}`;
    });
    const loadGraph = async () => {
      const baseRes = await api_order.getCampusGraphAPI();
      if (baseRes.code !== 0) {
        return;
      }
      const baseGraph = baseRes.data;
      const riders = (baseGraph.riders || []).map((rider) => ({
        riderId: Number(rider.riderId),
        label: `${rider.riderName || "骑手"}（载单${rider.activeLoad ?? 0}）`
      }));
      riderOptions.value = riders;
      if (!riders.length) {
        graph.value = baseGraph;
        selectedRiderId.value = void 0;
        return;
      }
      if (!selectedRiderId.value || !riders.some((item) => item.riderId === selectedRiderId.value)) {
        const highlighted = (baseGraph.riders || []).find((item) => item.highlight === 1);
        selectedRiderId.value = (highlighted == null ? void 0 : highlighted.riderId) || riders[0].riderId;
      }
      await loadGraphByRider(selectedRiderId.value);
    };
    const loadGraphByRider = async (riderId) => {
      const res = await api_order.getCampusGraphByRiderAPI(riderId);
      if (res.code === 0) {
        graph.value = res.data;
      }
    };
    const onSelectRider = async (event) => {
      const idx = Number(event.detail.value);
      const target = riderOptions.value[idx];
      if (!target)
        return;
      selectedRiderId.value = target.riderId;
      await loadGraphByRider(target.riderId);
    };
    const refreshGraph = async () => {
      if (selectedRiderId.value) {
        await loadGraphByRider(selectedRiderId.value);
        return;
      }
      await loadGraph();
    };
    common_vendor.onShow(async () => {
      await loadGraph();
    });
    return (_ctx, _cache) => {
      var _a, _b, _c, _d;
      return {
        a: common_vendor.t(selectedRiderLabel.value),
        b: riderOptions.value,
        c: selectedRiderIndex.value,
        d: common_vendor.o(onSelectRider),
        e: common_vendor.o(refreshGraph),
        f: common_vendor.p({
          title: "骑手调度路网",
          graph: graph.value,
          ["height-rpx"]: 520
        }),
        g: common_vendor.t(((_a = activeRider.value) == null ? void 0 : _a.riderName) || "-"),
        h: common_vendor.t(((_b = activeRider.value) == null ? void 0 : _b.riderPhone) || "-"),
        i: common_vendor.t(((_c = activeRider.value) == null ? void 0 : _c.activeLoad) ?? "-"),
        j: common_vendor.t(currentNodeName.value),
        k: common_vendor.t(((_d = graph.value) == null ? void 0 : _d.routeText) || "暂无任务路线")
      };
    };
  }
});
const MiniProgramPage = /* @__PURE__ */ common_vendor._export_sfc(_sfc_main, [["__scopeId", "data-v-418a4636"], ["__file", "D:/new1/hanye-take-out/hanye-take-out-uniapp/src/pages/rider/rider.vue"]]);
wx.createPage(MiniProgramPage);
