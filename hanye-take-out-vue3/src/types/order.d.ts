// 提交订单返回的信息
export type OrderSubmitVO = Partial<{
  id: number // 订单ID
  orderAmount: number // 订单金额
  orderNumber: string // 订单编号
  orderTime: Date // 下单时间
}>

// 订单信息
export type Order = {
  id: number // 订单id
  number: string // 订单号
  status: number // 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
  userId: number // 下单用户id
  addressBookId: number // 地址id
  orderTime: Date // 下单时间
  checkoutTime: Date // 结账时间
  payMethod: number // 支付方式 1微信，2支付宝
  payStatus: number // 支付状态 0未支付 1已支付 2退款
  amount: number // 实收金额
  remark: string // 备注
  userName: string // 用户名
  phone: string // 手机号
  address: string // 地址
  consignee: string // 收货人
  cancelReason: string // 订单取消原因
  rejectionReason: string // 订单拒绝原因
  cancelTime: Date // 订单取消时间
  estimatedDeliveryTime: Date // 预计送达时间
  deliveryStatus: number // 配送状态  1立即送出  0选择具体时间
  deliveryTime: Date // 送达时间
  packAmount: number // 打包费
  tablewareNumber: number // 餐具数量
  tablewareStatus: number // 餐具数量状态  1按餐量提供  0选择具体数量
}

// 订单详细菜品信息
export type OrderDetail = Partial<{
  id: number
  name: string // 名称
  orderId: number // 订单id
  dishId: number // 菜品id
  setmealId: number // 套餐id
  dishFlavor: string // 口味
  number: number // 数量
  amount: number // 金额
  pic: string // 图片
}>

// 订单所有信息
export type OrderVO = Order & {
  orderDetailList: OrderDetail[]
}

export type CampusPointVO = Partial<{
  nodeId: number
  name: string
  lng: number
  lat: number
}>

export type DispatchRiderCandidateVO = Partial<{
  riderId: number
  riderName: string
  riderPhone: string
  activeLoad: number
  score: number
  pickupEtaSec: number
  deliveryEtaSec: number
  totalEtaSec: number
  currentNodeId: number
  currentNodeName: string
}>

export type DispatchDetailVO = Partial<{
  orderId: number
  dispatchStatus: number
  riderId: number
  riderName: string
  riderPhone: string
  assignScore: number
  etaSec: number
  progressIndex: number
  totalPoints: number
  routeText: string
  routePoints: CampusPointVO[]
  riderCandidates: DispatchRiderCandidateVO[]
}>

export type CampusGraphNodeVO = Partial<{
  nodeId: number
  name: string
  lng: number
  lat: number
  nodeType: string
}>

export type CampusGraphEdgeVO = Partial<{
  edgeId: number
  fromNodeId: number
  toNodeId: number
  distanceM: number
  costTimeSec: number
  highlight: number
}>

export type CampusRiderVO = Partial<{
  riderId: number
  riderName: string
  riderPhone: string
  currentNodeId: number
  activeLoad: number
  highlight: number
}>

export type CampusGraphVO = Partial<{
  orderId: number
  riderId: number
  dispatchStatus: number
  dispatchStartTimeMs: number
  serverTimeMs: number
  routeText: string
  routeNodeIds: number[]
  nodes: CampusGraphNodeVO[]
  edges: CampusGraphEdgeVO[]
  riders: CampusRiderVO[]
}>

// 分页接口
export type PageVO<T> = {
  total: number
  records: T[]
}
