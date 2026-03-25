import request from '@/utils/request'
import type { CampusGraphVO, DispatchDetailVO } from '@/types/order'

// 查询列表页接口
export const getOrderDetailPageAPI = (params: any) => {
  return request({
    url: '/order/conditionSearch',
    method: 'get',
    params
  })
}

// 查看接口
export const queryOrderDetailByIdAPI = (params: any) => {
  return request({
    url: `/order/details/${params.orderId}`,
    method: 'get'
  })
}

// 派送接口
export const deliveryOrderAPI = (params: any) => {
  return request({
    url: `/order/delivery/${params.id}`,
    method: 'put'
  })
}

// 完成接口
export const completeOrderAPI = (params: any) => {
  return request({
    url: `/order/complete/${params.id}`,
    method: 'put'
  })
}

// 订单取消
export const orderCancelAPI = (params: any) => {
  return request({
    url: '/order/cancel',
    method: 'put',
    data: { ...params }
  })
}

// 接单
export const orderAcceptAPI = (params: any) => {
  console.log('接单params', params)
  return request({
    url: '/order/confirm',
    method: 'put',
    data: { ...params }
  })
}

// 拒单
export const orderRejectAPI = (params: any) => {
  return request({
    url: '/order/reject',
    method: 'put',
    data: { ...params }
  })
}

// 获取待处理，待派送，派送中数量
export const getOrderListByAPI = () => {
  return request({
    url: '/order/statistics',
    method: 'get'
  })
}

// 查询派单详情
export const getDispatchDetailAPI = (orderId: number) => {
  return request<{
    code: number
    msg: string
    data: DispatchDetailVO
  }>({
    url: `/order/dispatch/${orderId}`,
    method: 'get'
  })
}

// 人工改派
export const reassignDispatchAPI = (params: { orderId: number, riderId: number }) => {
  return request({
    url: '/order/dispatch/reassign',
    method: 'put',
    data: params
  })
}

// 校园路网（全量）
export const getCampusGraphAPI = () => {
  return request<{
    code: number
    msg: string
    data: CampusGraphVO
  }>({
    url: '/campus/graph',
    method: 'get'
  })
}

// 校园路网（按订单高亮）
export const getCampusGraphByOrderAPI = (orderId: number) => {
  return request<{
    code: number
    msg: string
    data: CampusGraphVO
  }>({
    url: `/campus/graph/order/${orderId}`,
    method: 'get'
  })
}

// 校园路网（按骑手高亮）
export const getCampusGraphByRiderAPI = (riderId: number) => {
  return request<{
    code: number
    msg: string
    data: CampusGraphVO
  }>({
    url: `/campus/graph/rider/${riderId}`,
    method: 'get'
  })
}
