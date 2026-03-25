<template>
  <view class="campus-card">
    <view class="campus-title">{{ title }}</view>
    <canvas
      :canvas-id="canvasId"
      :width="canvasWidth"
      :height="canvasHeight"
      class="campus-canvas"
      :style="{ width: `${canvasWidth}px`, height: `${canvasHeight}px` }"
      @touchstart="onCanvasTouch"
    ></canvas>

    <view class="legend-wrap">
      <view class="legend-item"><text class="house"></text><text>节点（小房子）</text></view>
      <view class="legend-item"><text class="moto"></text><text>骑手（大摩托）</text></view>
      <view class="legend-item"><text class="line normal"></text><text>普通边</text></view>
      <view class="legend-item"><text class="line hi"></text><text>高亮路径</text></view>
    </view>

    <view class="route-text" v-if="showRouteText">路线：{{ graph?.routeText || '-' }}</view>

    <view class="rider-popup-panel" v-if="selectedRiderInfo">
      <view class="rider-popup-title">{{ selectedRiderInfo.riderName }}（{{ selectedRiderInfo.badge }}）</view>
      <view class="rider-popup-line">电话：{{ selectedRiderInfo.riderPhone || '-' }}</view>
      <view class="rider-popup-line">载单：{{ selectedRiderInfo.activeLoad }}</view>
      <view class="rider-popup-line">位置：{{ selectedRiderInfo.nodeName }}</view>
      <view class="rider-popup-line">状态：{{ selectedRiderInfo.isMoving ? '行进中' : '待命/驻留' }}</view>
    </view>

    <view class="rider-list" v-if="riderRenderList.length">
      <view class="rider-item" v-for="item in riderRenderList" :key="`rider-${item.riderId}`" :class="{ active: item.highlight === 1 }">
        <text class="badge">{{ item.badge }}</text>
        <text class="txt">{{ item.riderName }}（载单{{ item.activeLoad }}）@ {{ item.nodeName }}</text>
      </view>
    </view>
  </view>
</template>

<script lang="ts" setup>
import {computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import type {CampusGraphEdgeVO, CampusGraphNodeVO, CampusGraphVO, CampusRiderVO} from '@/types/order'

const props = withDefaults(
  defineProps<{
    graph?: CampusGraphVO
    title?: string
    heightRpx?: number
    showRouteText?: boolean
  }>(),
  {
    graph: undefined,
    title: '校园路网图',
    heightRpx: 520,
    showRouteText: true,
  },
)

type NodeCoord = CampusGraphNodeVO & { x: number; y: number }
type RouteSegment = { fromNode: NodeCoord; toNode: NodeCoord; costSec: number; startSec: number; endSec: number }
type RiderRender = {
  riderId: number
  riderName: string
  riderPhone: string
  activeLoad: number
  nodeName: string
  x: number
  y: number
  rotateDeg: number
  highlight: number
  isMoving: boolean
  badge: string
}

const instance = getCurrentInstance()
const systemInfo = uni.getSystemInfoSync()
const canvasWidth = Math.max(280, Math.floor(systemInfo.windowWidth - 56))
const canvasHeight = computed(() => Math.max(220, Math.floor((props.heightRpx / 750) * systemInfo.windowWidth)))
const canvasId = `campus_graph_${Math.random().toString(36).slice(2, 8)}`

const nowMs = ref(Date.now())
const selectedRiderId = ref<number | null>(null)
const canvasRect = ref<{ left: number; top: number } | null>(null)
const riderHitAreas = ref<Array<{ x: number; y: number; r: number; riderId: number }>>([])
let tickTimer: ReturnType<typeof setInterval> | undefined

const edgeLabel = (edge: CampusGraphEdgeVO) => `${edge.distanceM || 0}m/${edge.costTimeSec || 0}s`

const graphNodes = computed<NodeCoord[]>(() => {
  const nodes = props.graph?.nodes || []
  if (!nodes.length) {
    return []
  }

  let minLng = Number(nodes[0].lng || 0)
  let maxLng = Number(nodes[0].lng || 0)
  let minLat = Number(nodes[0].lat || 0)
  let maxLat = Number(nodes[0].lat || 0)
  nodes.forEach((node) => {
    const lng = Number(node.lng || 0)
    const lat = Number(node.lat || 0)
    minLng = Math.min(minLng, lng)
    maxLng = Math.max(maxLng, lng)
    minLat = Math.min(minLat, lat)
    maxLat = Math.max(maxLat, lat)
  })
  if (minLng === maxLng) {
    minLng -= 0.001
    maxLng += 0.001
  }
  if (minLat === maxLat) {
    minLat -= 0.001
    maxLat += 0.001
  }

  const width = canvasWidth
  const height = canvasHeight.value
  const padding = 30
  const toX = (lng: number) => padding + ((lng - minLng) / (maxLng - minLng)) * (width - padding * 2)
  const toY = (lat: number) => height - padding - ((lat - minLat) / (maxLat - minLat)) * (height - padding * 2)

  return nodes.map((node) => ({
    ...node,
    x: toX(Number(node.lng || 0)),
    y: toY(Number(node.lat || 0)),
  }))
})

const nodeCoordMap = computed(() => {
  const map = new Map<number, NodeCoord>()
  graphNodes.value.forEach((node) => {
    if (node.nodeId !== undefined) {
      map.set(Number(node.nodeId), node)
    }
  })
  return map
})

const dedupEdges = computed(() => {
  const map = new Map<string, CampusGraphEdgeVO>()
  ;(props.graph?.edges || []).forEach((edge) => {
    if (edge.fromNodeId === undefined || edge.toNodeId === undefined) return
    const from = Number(edge.fromNodeId)
    const to = Number(edge.toNodeId)
    const key = from < to ? `${from}-${to}` : `${to}-${from}`
    const exists = map.get(key)
    if (!exists || Number(edge.highlight || 0) > Number(exists.highlight || 0)) {
      map.set(key, edge)
    }
  })
  return Array.from(map.values())
})

const edgeCostMap = computed(() => {
  const map = new Map<string, number>()
  ;(props.graph?.edges || []).forEach((edge) => {
    if (edge.fromNodeId === undefined || edge.toNodeId === undefined) return
    const from = Number(edge.fromNodeId)
    const to = Number(edge.toNodeId)
    const cost = Math.max(1, Number(edge.costTimeSec || 60))
    const key = `${from}-${to}`
    const exists = map.get(key)
    if (exists === undefined || cost < exists) {
      map.set(key, cost)
    }
  })
  return map
})

const routeMetrics = computed(() => {
  const ids = (props.graph?.routeNodeIds || []).map((id) => Number(id)).filter((id) => !Number.isNaN(id))
  const segments: RouteSegment[] = []
  if (ids.length < 2) {
    return { segments, totalSec: 0 }
  }
  let acc = 0
  for (let i = 0; i < ids.length - 1; i++) {
    const fromId = ids[i]
    const toId = ids[i + 1]
    const fromNode = nodeCoordMap.value.get(fromId)
    const toNode = nodeCoordMap.value.get(toId)
    if (!fromNode || !toNode) continue
    const cost = edgeCostMap.value.get(`${fromId}-${toId}`) ?? edgeCostMap.value.get(`${toId}-${fromId}`) ?? 60
    const costSec = Math.max(1, Number(cost))
    segments.push({ fromNode, toNode, costSec, startSec: acc, endSec: acc + costSec })
    acc += costSec
  }
  return { segments, totalSec: acc }
})

const liveElapsedSec = computed(() => {
  const graph = props.graph
  if (!graph || Number(graph.dispatchStatus) !== 1) {
    return 0
  }
  const startMs = Number(graph.dispatchStartTimeMs || 0)
  const serverMs = Number(graph.serverTimeMs || 0)
  if (startMs <= 0 || serverMs < startMs) {
    return 0
  }
  const baseElapsed = (serverMs - startMs) / 1000
  const drift = Math.max(0, (nowMs.value - serverMs) / 1000)
  return baseElapsed + drift
})

const focusRiderId = computed(() => {
  if (props.graph?.riderId !== undefined && props.graph.riderId !== null) {
    return Number(props.graph.riderId)
  }
  const hit = (props.graph?.riders || []).find((rider) => Number(rider.highlight || 0) === 1)
  return hit?.riderId === undefined ? -1 : Number(hit.riderId)
})

const riderRenderList = computed<RiderRender[]>(() => {
  const riders = props.graph?.riders || []
  const nodeMap = nodeCoordMap.value
  const list: RiderRender[] = []
  const dynamicRiderId = focusRiderId.value

  if (Number(props.graph?.dispatchStatus) === 1 && dynamicRiderId > 0 && routeMetrics.value.segments.length) {
    const rider = riders.find((item) => Number(item.riderId) === dynamicRiderId)
    if (rider) {
      const elapsed = liveElapsedSec.value
      const metrics = routeMetrics.value
      let x = metrics.segments[0].fromNode.x
      let y = metrics.segments[0].fromNode.y
      let rotateDeg = 0
      let nodeName = metrics.segments[0].fromNode.name || '-'

      if (elapsed >= metrics.totalSec) {
        const last = metrics.segments[metrics.segments.length - 1]
        x = last.toNode.x
        y = last.toNode.y
        rotateDeg = Math.atan2(last.toNode.y - last.fromNode.y, last.toNode.x - last.fromNode.x) * 180 / Math.PI
        nodeName = last.toNode.name || '-'
      } else {
        const segment = metrics.segments.find((item) => elapsed < item.endSec) || metrics.segments[metrics.segments.length - 1]
        const ratio = Math.max(0, Math.min(1, (elapsed - segment.startSec) / segment.costSec))
        x = segment.fromNode.x + (segment.toNode.x - segment.fromNode.x) * ratio
        y = segment.fromNode.y + (segment.toNode.y - segment.fromNode.y) * ratio
        rotateDeg = Math.atan2(segment.toNode.y - segment.fromNode.y, segment.toNode.x - segment.fromNode.x) * 180 / Math.PI
        nodeName = ratio >= 0.5 ? (segment.toNode.name || '-') : (segment.fromNode.name || '-')
      }

      list.push({
        riderId: Number(rider.riderId),
        riderName: rider.riderName || '骑手',
        riderPhone: rider.riderPhone || '',
        activeLoad: Number(rider.activeLoad || 0),
        nodeName,
        x,
        y,
        rotateDeg,
        highlight: 1,
        isMoving: true,
        badge: String(rider.riderId ?? ''),
      })
    }
  }

  const grouped = new Map<number, CampusRiderVO[]>()
  riders.forEach((rider) => {
    const riderId = Number(rider.riderId || 0)
    if (riderId === dynamicRiderId && Number(props.graph?.dispatchStatus) === 1) {
      return
    }
    const nodeId = Number(rider.currentNodeId)
    if (!grouped.has(nodeId)) {
      grouped.set(nodeId, [])
    }
    grouped.get(nodeId)!.push(rider)
  })

  const offsets = [
    {dx: -18, dy: 12},
    {dx: -26, dy: 3},
    {dx: -10, dy: 20},
    {dx: -30, dy: 16},
    {dx: -6, dy: 28},
    {dx: -24, dy: -8},
    {dx: -14, dy: -16},
    {dx: -34, dy: -2},
  ]

  grouped.forEach((items, nodeId) => {
    const node = nodeMap.get(nodeId)
    if (!node) return
    items
      .slice()
      .sort((a, b) => Number(a.riderId || 0) - Number(b.riderId || 0))
      .forEach((rider, idx) => {
        const base = offsets[idx % offsets.length]
        const ring = Math.floor(idx / offsets.length)
        const spread = ring * 6
        list.push({
          riderId: Number(rider.riderId || 0),
          riderName: rider.riderName || '骑手',
          riderPhone: rider.riderPhone || '',
          activeLoad: Number(rider.activeLoad || 0),
          nodeName: node.name || '-',
          x: node.x + base.dx - spread,
          y: node.y + base.dy + spread * 0.5,
          rotateDeg: 0,
          highlight: Number(rider.highlight || 0),
          isMoving: false,
          badge: String(rider.riderId ?? ''),
        })
      })
  })

  return list.sort((a, b) => b.highlight - a.highlight)
})

const selectedRiderInfo = computed(() => {
  if (selectedRiderId.value === null) {
    return null
  }
  return riderRenderList.value.find((item) => item.riderId === selectedRiderId.value) || null
})

watch(
  () => props.graph?.orderId,
  () => {
    selectedRiderId.value = null
  },
)

watch(riderRenderList, (list) => {
  if (selectedRiderId.value === null) return
  const exists = list.some((item) => item.riderId === selectedRiderId.value)
  if (!exists) {
    selectedRiderId.value = null
  }
}, {deep: true})

const drawRoundRect = (ctx: UniApp.CanvasContext, x: number, y: number, w: number, h: number, r: number) => {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + w - radius, y)
  ctx.arcTo(x + w, y, x + w, y + radius, radius)
  ctx.lineTo(x + w, y + h - radius)
  ctx.arcTo(x + w, y + h, x + w - radius, y + h, radius)
  ctx.lineTo(x + radius, y + h)
  ctx.arcTo(x, y + h, x, y + h - radius, radius)
  ctx.lineTo(x, y + radius)
  ctx.arcTo(x, y, x + radius, y, radius)
  ctx.closePath()
}

const drawHouse = (ctx: UniApp.CanvasContext, x: number, y: number, nodeType?: string, highlight?: boolean) => {
  const roofColor = nodeType === 'SHOP' ? '#f97316' : nodeType === 'DROPOFF' ? '#2563eb' : '#64748b'
  const bodyColor = nodeType === 'SHOP' ? '#fdba74' : nodeType === 'DROPOFF' ? '#93c5fd' : '#cbd5e1'

  if (highlight) {
    ctx.beginPath()
    ctx.arc(x, y, 11, 0, 2 * Math.PI)
    ctx.setFillStyle('rgba(14,165,233,0.16)')
    ctx.fill()
  }

  ctx.setFillStyle(roofColor)
  ctx.beginPath()
  ctx.moveTo(x - 8, y - 1)
  ctx.lineTo(x, y - 10)
  ctx.lineTo(x + 8, y - 1)
  ctx.closePath()
  ctx.fill()

  ctx.setFillStyle(bodyColor)
  drawRoundRect(ctx, x - 7, y - 1, 14, 11, 2)
  ctx.fill()

  ctx.setFillStyle('rgba(255,255,255,0.92)')
  drawRoundRect(ctx, x - 2, y + 4, 4, 6, 1)
  ctx.fill()
  drawRoundRect(ctx, x - 5.8, y + 1.4, 3, 2.4, 0.8)
  ctx.fill()
  drawRoundRect(ctx, x + 2.8, y + 1.4, 3, 2.4, 0.8)
  ctx.fill()
}

const drawMoto = (ctx: UniApp.CanvasContext, rider: RiderRender, selected: boolean) => {
  const x = rider.x
  const y = rider.y
  const rotate = (rider.rotateDeg * Math.PI) / 180

  ctx.save()
  ctx.setFillStyle('rgba(100,116,139,0.23)')
  ctx.beginPath()
  ctx.ellipse(x, y + 10, rider.isMoving ? 12 : 10, 3.2, 0, 0, 2 * Math.PI)
  ctx.fill()
  ctx.restore()

  ctx.save()
  ctx.translate(x, y)
  ctx.rotate(rotate)

  ctx.setFillStyle('#1f2937')
  ctx.beginPath()
  ctx.arc(-9.2, 6.8, 3.9, 0, 2 * Math.PI)
  ctx.arc(9.5, 6.8, 3.9, 0, 2 * Math.PI)
  ctx.fill()

  ctx.setFillStyle(rider.highlight === 1 ? '#f59e0b' : '#0ea5e9')
  ctx.beginPath()
  ctx.moveTo(-12, 2.8)
  ctx.lineTo(-7, -1.8)
  ctx.lineTo(5.8, -1.8)
  ctx.quadraticCurveTo(8.8, -1.8, 10.8, 0.7)
  ctx.lineTo(13.2, 3.6)
  ctx.lineTo(3.5, 3.6)
  ctx.quadraticCurveTo(1.8, 3.6, 0.9, 4.4)
  ctx.lineTo(-1.4, 6.4)
  ctx.lineTo(-9.8, 6.4)
  ctx.closePath()
  ctx.fill()

  ctx.setStrokeStyle(rider.highlight === 1 ? '#92400e' : '#0f172a')
  ctx.setLineWidth(1.3)
  ctx.beginPath()
  ctx.moveTo(-7.5, 5.1)
  ctx.lineTo(-2.8, 5.1)
  ctx.lineTo(0.8, 2.1)
  ctx.lineTo(7.8, 2.1)
  ctx.lineTo(12.8, 5.1)
  ctx.stroke()

  ctx.setStrokeStyle(rider.highlight === 1 ? '#fef3c7' : '#38bdf8')
  ctx.setLineWidth(1)
  ctx.beginPath()
  ctx.moveTo(-8.2, -1.8)
  ctx.lineTo(-3.4, -7.1)
  ctx.lineTo(-1.4, -7.1)
  ctx.lineTo(-2.3, -2.1)
  ctx.stroke()

  ctx.setFillStyle(rider.highlight === 1 ? '#fbbf24' : '#38bdf8')
  drawRoundRect(ctx, 4.9, -5.1, 5.6, 3.5, 1)
  ctx.fill()

  ctx.setFillStyle('#fef08a')
  ctx.beginPath()
  ctx.arc(13.6, -0.4, 1.4, 0, 2 * Math.PI)
  ctx.fill()

  ctx.restore()

  const dotX = x + 16
  const dotY = y - 12
  ctx.beginPath()
  ctx.arc(dotX, dotY, 7, 0, 2 * Math.PI)
  ctx.setFillStyle(rider.highlight === 1 ? '#fef3c7' : '#e2f2ff')
  ctx.fill()
  ctx.setStrokeStyle(rider.highlight === 1 ? '#f59e0b' : '#0ea5e9')
  ctx.setLineWidth(1)
  ctx.stroke()

  if (selected) {
    ctx.beginPath()
    ctx.arc(dotX, dotY, 9.3, 0, 2 * Math.PI)
    ctx.setStrokeStyle('rgba(245,158,11,0.8)')
    ctx.setLineWidth(1.4)
    ctx.stroke()
  }

  ctx.setTextAlign('center')
  ctx.setTextBaseline('middle')
  ctx.setFillStyle('#0f172a')
  ctx.setFontSize(8)
  ctx.fillText(rider.badge, dotX, dotY + 0.2)
}

const drawPopupOnCanvas = (ctx: UniApp.CanvasContext, rider: RiderRender) => {
  const width = 170
  const height = 82
  const cw = canvasWidth
  const ch = canvasHeight.value
  let x = rider.x + 16
  let y = rider.y - 78
  if (x + width > cw - 8) x = rider.x - width - 12
  if (y < 8) y = rider.y + 14
  x = Math.max(8, Math.min(x, cw - width - 8))
  y = Math.max(8, Math.min(y, ch - height - 8))

  drawRoundRect(ctx, x, y, width, height, 8)
  ctx.setFillStyle('rgba(255,255,255,0.96)')
  ctx.fill()
  ctx.setStrokeStyle('#bae6fd')
  ctx.setLineWidth(1)
  ctx.stroke()

  ctx.setTextAlign('left')
  ctx.setTextBaseline('middle')
  ctx.setFillStyle('#0f172a')
  ctx.setFontSize(11)
  ctx.fillText(`${rider.riderName}（${rider.badge}）`, x + 10, y + 14)
  ctx.setFillStyle('#334155')
  ctx.setFontSize(10)
  ctx.fillText(`电话：${rider.riderPhone || '-'}`, x + 10, y + 30)
  ctx.fillText(`载单：${rider.activeLoad}`, x + 10, y + 45)
  ctx.fillText(`位置：${rider.nodeName}`, x + 10, y + 60)
  ctx.fillText(`状态：${rider.isMoving ? '行进中' : '待命/驻留'}`, x + 10, y + 75)
}

const drawGraph = () => {
  const ctx = uni.createCanvasContext(canvasId, instance?.proxy as any)
  const graph = props.graph
  const width = canvasWidth
  const height = canvasHeight.value

  ctx.clearRect(0, 0, width, height)
  ctx.setFillStyle('#f7fbff')
  ctx.fillRect(0, 0, width, height)

  const nodes = graphNodes.value
  if (!nodes.length) {
    ctx.setFillStyle('#8b8b8b')
    ctx.setFontSize(14)
    ctx.fillText('暂无路网数据', width / 2 - 42, height / 2)
    ctx.draw()
    riderHitAreas.value = []
    return
  }

  const nodeMap = nodeCoordMap.value
  const edges = dedupEdges.value

  edges.forEach((edge) => {
    const fromNode = nodeMap.get(Number(edge.fromNodeId))
    const toNode = nodeMap.get(Number(edge.toNodeId))
    if (!fromNode || !toNode) return
    ctx.beginPath()
    ctx.moveTo(fromNode.x, fromNode.y)
    ctx.lineTo(toNode.x, toNode.y)
    if (edge.highlight === 1) {
      ctx.setStrokeStyle('#0ea5e9')
      ctx.setLineWidth(2.8)
      ctx.setLineDash([9, 7])
    } else {
      ctx.setStrokeStyle('#94a3b8')
      ctx.setLineWidth(1.9)
      ctx.setLineDash([])
    }
    ctx.stroke()
    ctx.setLineDash([])
  })

  edges.forEach((edge) => {
    const fromNode = nodeMap.get(Number(edge.fromNodeId))
    const toNode = nodeMap.get(Number(edge.toNodeId))
    if (!fromNode || !toNode) return
    const x = (fromNode.x + toNode.x) / 2
    const y = (fromNode.y + toNode.y) / 2
    drawRoundRect(ctx, x - 38, y - 9, 76, 18, 4)
    ctx.setFillStyle('rgba(255,255,255,0.9)')
    ctx.fill()
    ctx.setStrokeStyle('#dbeafe')
    ctx.setLineWidth(1)
    ctx.stroke()
    ctx.setTextAlign('center')
    ctx.setTextBaseline('middle')
    ctx.setFillStyle('#374151')
    ctx.setFontSize(9)
    ctx.fillText(edgeLabel(edge), x, y)
  })

  const routeNodeSet = new Set<number>((graph?.routeNodeIds || []).map((id) => Number(id)))
  nodes.forEach((node) => {
    drawHouse(ctx, node.x, node.y, node.nodeType, routeNodeSet.has(Number(node.nodeId)))
    ctx.setTextAlign('left')
    ctx.setTextBaseline('middle')
    ctx.setFillStyle('#0f172a')
    ctx.setFontSize(10)
    ctx.fillText(node.name || `节点${node.nodeId}`, node.x + 10, node.y - 10)
  })

  const hits: Array<{ x: number; y: number; r: number; riderId: number }> = []
  riderRenderList.value.forEach((rider) => {
    const selected = selectedRiderId.value !== null && rider.riderId === selectedRiderId.value
    drawMoto(ctx, rider, selected)
    hits.push({ x: rider.x, y: rider.y, r: 13, riderId: rider.riderId })
  })

  riderHitAreas.value = hits

  if (selectedRiderInfo.value) {
    drawPopupOnCanvas(ctx, selectedRiderInfo.value)
  }

  ctx.draw()
}

const refreshCanvasRect = () => {
  return new Promise<void>((resolve) => {
    const query = uni.createSelectorQuery().in(instance?.proxy as any)
    query
      .select('.campus-canvas')
      .boundingClientRect((rect: any) => {
        if (rect && typeof rect.left === 'number' && typeof rect.top === 'number') {
          canvasRect.value = { left: rect.left, top: rect.top }
        }
      })
      .exec(() => resolve())
  })
}

const onCanvasTouch = async (e: any) => {
  const touch = e.changedTouches?.[0] || e.touches?.[0]
  if (!touch) {
    return
  }
  if (!canvasRect.value) {
    await refreshCanvasRect()
  }
  const rect = canvasRect.value
  if (!rect) {
    return
  }
  const localX = Number(touch.x ?? touch.clientX ?? touch.pageX) - rect.left
  const localY = Number(touch.y ?? touch.clientY ?? touch.pageY) - rect.top

  let hitId: number | null = null
  for (let i = riderHitAreas.value.length - 1; i >= 0; i--) {
    const area = riderHitAreas.value[i]
    const dx = localX - area.x
    const dy = localY - area.y
    if (dx * dx + dy * dy <= area.r * area.r) {
      hitId = area.riderId
      break
    }
  }
  selectedRiderId.value = hitId
  nextTick(() => drawGraph())
}

watch(
  () => [props.graph, riderRenderList.value, selectedRiderId.value],
  () => {
    nextTick(() => {
      drawGraph()
      refreshCanvasRect()
    })
  },
  { deep: true, immediate: true },
)

onMounted(() => {
  tickTimer = setInterval(() => {
    nowMs.value = Date.now()
  }, 220)
  nextTick(() => refreshCanvasRect())
})

onBeforeUnmount(() => {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = undefined
  }
})
</script>

<style lang="less" scoped>
.campus-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 20rpx;
  border: 1px solid #e7eef2;
}

.campus-title {
  font-size: 30rpx;
  color: #222;
  font-weight: 600;
  margin-bottom: 14rpx;
}

.campus-canvas {
  width: 100%;
  border-radius: 12rpx;
  border: 1px solid #edf0f3;
  background: #f7fbff;
}

.legend-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx 24rpx;
  margin-top: 14rpx;
}

.legend-item {
  display: flex;
  align-items: center;
  font-size: 22rpx;
  color: #666;
}

.house {
  width: 16rpx;
  height: 16rpx;
  margin-right: 8rpx;
  background: linear-gradient(180deg, #f97316 0%, #fdba74 100%);
  clip-path: polygon(50% 0%, 100% 38%, 100% 100%, 0 100%, 0 38%);
}

.moto {
  width: 20rpx;
  height: 10rpx;
  border-radius: 6rpx;
  margin-right: 8rpx;
  background: linear-gradient(90deg, #0ea5e9 0%, #38bdf8 100%);
}

.line {
  width: 24rpx;
  height: 0;
  border-top: 4rpx solid #9ca3af;
  margin-right: 8rpx;
}

.line.hi {
  border-top-color: #0ea5e9;
}

.route-text {
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #4b5563;
  line-height: 34rpx;
}

.rider-popup-panel {
  margin-top: 10rpx;
  border: 1px solid #bae6fd;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.96);
  padding: 10rpx 14rpx;
}

.rider-popup-title {
  font-size: 24rpx;
  color: #0f172a;
  font-weight: 700;
  margin-bottom: 4rpx;
}

.rider-popup-line {
  font-size: 20rpx;
  color: #334155;
  line-height: 30rpx;
}

.rider-list {
  margin-top: 10rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.rider-item {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.rider-item.active .txt {
  color: #b45309;
  font-weight: 600;
}

.rider-item .badge {
  width: 24rpx;
  height: 24rpx;
  line-height: 24rpx;
  border-radius: 50%;
  background: #0ea5e9;
  color: #fff;
  font-size: 16rpx;
  text-align: center;
  font-weight: 700;
}

.rider-item .txt {
  font-size: 22rpx;
  color: #374151;
}
</style>
