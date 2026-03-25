"use strict";
const utils_http = require("../utils/http.js");
const submitOrderAPI = (params) => {
  return utils_http.http({
    url: "/user/order/submit",
    method: "POST",
    data: params
  });
};
const payOrderAPI = (params) => {
  return utils_http.http({
    url: "/user/order/payment",
    method: "PUT",
    data: params
  });
};
const getUnPayOrderAPI = () => {
  return utils_http.http({
    url: "/user/order/unPayOrderCount",
    method: "GET"
  });
};
const getOrderAPI = (id) => {
  console.log("byd !!! id", id);
  return utils_http.http({
    url: `/user/order/orderDetail/${id}`,
    method: "GET"
  });
};
const getOrderTrackAPI = (id) => {
  return utils_http.http({
    url: `/user/order/track/${id}`,
    method: "GET"
  });
};
const getCampusGraphAPI = () => {
  return utils_http.http({
    url: "/user/campus/graph",
    method: "GET"
  });
};
const getCampusGraphByOrderAPI = (id) => {
  return utils_http.http({
    url: `/user/campus/graph/order/${id}`,
    method: "GET"
  });
};
const getCampusGraphByRiderAPI = (riderId) => {
  return utils_http.http({
    url: `/user/campus/graph/rider/${riderId}`,
    method: "GET"
  });
};
const getOrderPageAPI = (params) => {
  console.log("params", params);
  return utils_http.http({
    url: "/user/order/historyOrders",
    method: "GET",
    data: params
  });
};
const cancelOrderAPI = (id) => {
  return utils_http.http({
    url: `/user/order/cancel/${id}`,
    method: "PUT"
  });
};
const reOrderAPI = (id) => {
  return utils_http.http({
    url: `/user/order/reOrder/${id}`,
    method: "POST"
  });
};
const urgeOrderAPI = (id) => {
  return utils_http.http({
    url: `/user/order/reminder/${id}`,
    method: "GET"
  });
};
exports.cancelOrderAPI = cancelOrderAPI;
exports.getCampusGraphAPI = getCampusGraphAPI;
exports.getCampusGraphByOrderAPI = getCampusGraphByOrderAPI;
exports.getCampusGraphByRiderAPI = getCampusGraphByRiderAPI;
exports.getOrderAPI = getOrderAPI;
exports.getOrderPageAPI = getOrderPageAPI;
exports.getOrderTrackAPI = getOrderTrackAPI;
exports.getUnPayOrderAPI = getUnPayOrderAPI;
exports.payOrderAPI = payOrderAPI;
exports.reOrderAPI = reOrderAPI;
exports.submitOrderAPI = submitOrderAPI;
exports.urgeOrderAPI = urgeOrderAPI;
