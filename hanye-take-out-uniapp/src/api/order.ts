import { http } from '@/utils/http'
import type {
  CampusGraphVO,
  OrderPageDTO,
  OrderSubmitVO,
  OrderVO,
  PageVO,
  OrderPaymentDTO,
  OrderTrackVO,
} from '@/types/order'

// 用户下单
export const submitOrderAPI = (params: any) => {
  return http<OrderSubmitVO>({
    url: '/user/order/submit',
    method: 'POST',
    data: params,
  })
}

// 支付订单
export const payOrderAPI = (params: OrderPaymentDTO) => {
  return http({
    url: '/user/order/payment',
    method: 'PUT',
    data: params,
  })
}

// 未支付订单数量
export const getUnPayOrderAPI = () => {
  return http<number>({
    url: '/user/order/unPayOrderCount',
    method: 'GET',
  })
}

// 根据订单id获取订单详情
export const getOrderAPI = (id: number) => {
  console.log('byd !!! id', id)
  return http<OrderVO>({
    url: `/user/order/orderDetail/${id}`,
    method: 'GET',
  })
}

// 查询订单轨迹信息
export const getOrderTrackAPI = (id: number) => {
  return http<OrderTrackVO>({
    url: `/user/order/track/${id}`,
    method: 'GET',
  })
}

// 查询校园路网（全量）
export const getCampusGraphAPI = () => {
  return http<CampusGraphVO>({
    url: '/user/campus/graph',
    method: 'GET',
  })
}

// 查询校园路网（按订单高亮）
export const getCampusGraphByOrderAPI = (id: number) => {
  return http<CampusGraphVO>({
    url: `/user/campus/graph/order/${id}`,
    method: 'GET',
  })
}

// 查询校园路网（按骑手高亮）
export const getCampusGraphByRiderAPI = (riderId: number) => {
  return http<CampusGraphVO>({
    url: `/user/campus/graph/rider/${riderId}`,
    method: 'GET',
  })
}

// 查询历史订单
export const getOrderPageAPI = (params: OrderPageDTO) => {
  console.log('params', params)
  return http<PageVO<OrderVO>>({
    url: '/user/order/historyOrders',
    method: 'GET',
    data: params,
  })
}

// 取消订单
export const cancelOrderAPI = (id: number) => {
  return http({
    url: `/user/order/cancel/${id}`,
    method: 'PUT',
  })
}

// 再来一单，要批量加入菜品到购物车，所以是POST请求
export const reOrderAPI = (id: number) => {
  return http({
    url: `/user/order/reOrder/${id}`,
    method: 'POST',
  })
}

// 催单
export const urgeOrderAPI = (id: number) => {
  return http({
    url: `/user/order/reminder/${id}`,
    method: 'GET',
  })
}
