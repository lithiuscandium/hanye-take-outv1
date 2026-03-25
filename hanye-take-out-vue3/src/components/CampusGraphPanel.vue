<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { CampusGraphEdgeVO, CampusGraphNodeVO, CampusGraphVO, CampusRiderVO } from '@/types/order'

const props = withDefaults(defineProps<{
  graph?: CampusGraphVO
  height?: number
}>(), {
  graph: undefined,
  height: 360,
})

const viewWidth = 900
const viewHeight = 520
const plotPadding = 70
const minZoom = 0.65
const maxZoom = 2.8

type GraphNodeWithCoord = CampusGraphNodeVO & { x: number; y: number }
type Rect = { x1: number; y1: number; x2: number; y2: number }
type NodeLabelPlacement = {
  nodeId: number
  name: string
  x: number
  y: number
  textAnchor: 'start' | 'middle' | 'end'
  bgX: number
  bgY: number
  bgW: number
  bgH: number
}
type RiderMarker = CampusRiderVO & {
  x: number
  y: number
  nodeName?: string
  badge: string
  rotateDeg: number
  isMoving: boolean
}
type RiderPopup = {
  x: number
  y: number
  width: number
  height: number
  title: string
  phone: string
  loadText: string
  nodeText: string
  motionText: string
}

type RouteSegment = {
  fromNode: GraphNodeWithCoord
  toNode: GraphNodeWithCoord
  costSec: number
  startSec: number
  endSec: number
}

const svgRef = ref<SVGSVGElement>()
const zoomScale = ref(1)
const panX = ref(0)
const panY = ref(0)
const isDragging = ref(false)
const dragStartClientX = ref(0)
const dragStartClientY = ref(0)
const dragStartPanX = ref(0)
const dragStartPanY = ref(0)

const contentTransform = computed(() => `translate(${panX.value} ${panY.value}) scale(${zoomScale.value})`)
const zoomPercentText = computed(() => `${Math.round(zoomScale.value * 100)}%`)

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

const normalizeTransform = (scale: number, tx: number, ty: number) => {
  const nextScale = clamp(scale, minZoom, maxZoom)
  const pad = 140
  const minTx = viewWidth - viewWidth * nextScale - pad
  const maxTx = pad
  const minTy = viewHeight - viewHeight * nextScale - pad
  const maxTy = pad
  return {
    scale: nextScale,
    tx: clamp(tx, minTx, maxTx),
    ty: clamp(ty, minTy, maxTy),
  }
}

const applyTransform = (scale: number, tx: number, ty: number) => {
  const next = normalizeTransform(scale, tx, ty)
  zoomScale.value = next.scale
  panX.value = next.tx
  panY.value = next.ty
}

const resetView = () => {
  applyTransform(1, 0, 0)
}

const resolveViewPoint = (clientX: number, clientY: number) => {
  const el = svgRef.value
  if (!el) {
    return { x: viewWidth / 2, y: viewHeight / 2 }
  }
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) {
    return { x: viewWidth / 2, y: viewHeight / 2 }
  }
  return {
    x: ((clientX - rect.left) / rect.width) * viewWidth,
    y: ((clientY - rect.top) / rect.height) * viewHeight,
  }
}

const zoomAtPoint = (factor: number, anchorX: number, anchorY: number) => {
  const oldScale = zoomScale.value
  const targetScale = clamp(oldScale * factor, minZoom, maxZoom)
  const ratio = targetScale / oldScale
  const targetTx = anchorX - (anchorX - panX.value) * ratio
  const targetTy = anchorY - (anchorY - panY.value) * ratio
  applyTransform(targetScale, targetTx, targetTy)
}

const zoomIn = () => zoomAtPoint(1.15, viewWidth / 2, viewHeight / 2)
const zoomOut = () => zoomAtPoint(1 / 1.15, viewWidth / 2, viewHeight / 2)

const onWheel = (event: WheelEvent) => {
  const point = resolveViewPoint(event.clientX, event.clientY)
  const factor = event.deltaY < 0 ? 1.12 : 1 / 1.12
  zoomAtPoint(factor, point.x, point.y)
}

const onMouseDown = (event: MouseEvent) => {
  if (event.button !== 0) {
    return
  }
  isDragging.value = true
  dragStartClientX.value = event.clientX
  dragStartClientY.value = event.clientY
  dragStartPanX.value = panX.value
  dragStartPanY.value = panY.value
}

const onWindowMouseMove = (event: MouseEvent) => {
  if (!isDragging.value) {
    return
  }
  const el = svgRef.value
  if (!el) {
    return
  }
  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) {
    return
  }
  const dx = ((event.clientX - dragStartClientX.value) / rect.width) * viewWidth
  const dy = ((event.clientY - dragStartClientY.value) / rect.height) * viewHeight
  applyTransform(zoomScale.value, dragStartPanX.value + dx, dragStartPanY.value + dy)
}

const onWindowMouseUp = () => {
  isDragging.value = false
}

const hasGraphData = computed(() => !!props.graph && (props.graph.nodes?.length || 0) > 0)

const nodeMap = computed(() => {
  const map = new Map<number, CampusGraphNodeVO>()
  for (const node of props.graph?.nodes || []) {
    if (node.nodeId !== undefined) {
      map.set(Number(node.nodeId), node)
    }
  }
  return map
})

const bounds = computed(() => {
  const nodes = props.graph?.nodes || []
  if (!nodes.length) {
    return { minLng: 0, maxLng: 1, minLat: 0, maxLat: 1 }
  }
  let minLng = Number(nodes[0].lng || 0)
  let maxLng = Number(nodes[0].lng || 0)
  let minLat = Number(nodes[0].lat || 0)
  let maxLat = Number(nodes[0].lat || 0)
  for (const node of nodes) {
    const lng = Number(node.lng || 0)
    const lat = Number(node.lat || 0)
    minLng = Math.min(minLng, lng)
    maxLng = Math.max(maxLng, lng)
    minLat = Math.min(minLat, lat)
    maxLat = Math.max(maxLat, lat)
  }
  if (minLng === maxLng) {
    minLng -= 0.001
    maxLng += 0.001
  }
  if (minLat === maxLat) {
    minLat -= 0.001
    maxLat += 0.001
  }
  return { minLng, maxLng, minLat, maxLat }
})

const toX = (lng: number) => {
  const { minLng, maxLng } = bounds.value
  const rate = (lng - minLng) / (maxLng - minLng)
  return plotPadding + rate * (viewWidth - plotPadding * 2)
}

const toY = (lat: number) => {
  const { minLat, maxLat } = bounds.value
  const rate = (lat - minLat) / (maxLat - minLat)
  return viewHeight - plotPadding - rate * (viewHeight - plotPadding * 2)
}

const graphNodes = computed<GraphNodeWithCoord[]>(() => {
  return (props.graph?.nodes || []).map((node) => {
    const lng = Number(node.lng || 0)
    const lat = Number(node.lat || 0)
    return { ...node, x: toX(lng), y: toY(lat) }
  })
})

const graphNodeMapWithCoord = computed(() => {
  const map = new Map<number, GraphNodeWithCoord>()
  for (const item of graphNodes.value) {
    if (item.nodeId !== undefined) {
      map.set(Number(item.nodeId), item)
    }
  }
  return map
})

const overlapArea = (a: Rect, b: Rect) => {
  const w = Math.max(0, Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1))
  const h = Math.max(0, Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1))
  return w * h
}

const toRect = (centerX: number, centerY: number, width: number, height: number): Rect => {
  const hw = width / 2
  const hh = height / 2
  return { x1: centerX - hw, y1: centerY - hh, x2: centerX + hw, y2: centerY + hh }
}

const nodeLabelPlacements = computed<NodeLabelPlacement[]>(() => {
  const placements: NodeLabelPlacement[] = []
  const placedRects: Rect[] = []
  const candidateOffsets = [
    { dx: 14, dy: -18, anchor: 'start' as const },
    { dx: 14, dy: 18, anchor: 'start' as const },
    { dx: -14, dy: -18, anchor: 'end' as const },
    { dx: -14, dy: 18, anchor: 'end' as const },
    { dx: 0, dy: -26, anchor: 'middle' as const },
    { dx: 0, dy: 26, anchor: 'middle' as const },
  ]

  const sortedNodes = graphNodes.value.slice().sort((a, b) => Number(a.nodeId || 0) - Number(b.nodeId || 0))
  sortedNodes.forEach((node) => {
    const name = node.name || `节点${node.nodeId}`
    const bgW = Math.max(62, name.length * 13 + 16)
    const bgH = 22
    let best: NodeLabelPlacement | undefined
    let bestScore = Number.MAX_VALUE

    candidateOffsets.forEach((candidate, idx) => {
      const x = node.x + candidate.dx
      const y = node.y + candidate.dy
      let centerX = x
      if (candidate.anchor === 'start') {
        centerX = x + bgW / 2
      } else if (candidate.anchor === 'end') {
        centerX = x - bgW / 2
      }
      const rect = toRect(centerX, y - 4, bgW, bgH)
      let score = idx * 0.01
      for (const used of placedRects) {
        score += overlapArea(rect, used)
      }
      score += overlapArea(rect, toRect(node.x, node.y, 24, 24)) * 1.5
      if (score < bestScore) {
        bestScore = score
        best = {
          nodeId: Number(node.nodeId),
          name,
          x,
          y,
          textAnchor: candidate.anchor,
          bgX: rect.x1,
          bgY: rect.y1,
          bgW,
          bgH,
        }
      }
    })

    if (best) {
      placements.push(best)
      placedRects.push({
        x1: best.bgX,
        y1: best.bgY,
        x2: best.bgX + best.bgW,
        y2: best.bgY + best.bgH,
      })
    }
  })

  return placements
})

const nodeLabelMap = computed(() => {
  const map = new Map<number, NodeLabelPlacement>()
  for (const placement of nodeLabelPlacements.value) {
    map.set(placement.nodeId, placement)
  }
  return map
})

const edgeCostMap = computed(() => {
  const map = new Map<string, number>()
  for (const edge of props.graph?.edges || []) {
    if (edge.fromNodeId === undefined || edge.toNodeId === undefined) {
      continue
    }
    const key = `${Number(edge.fromNodeId)}-${Number(edge.toNodeId)}`
    const cost = Math.max(1, Number(edge.costTimeSec || 60))
    const exists = map.get(key)
    if (exists === undefined || cost < exists) {
      map.set(key, cost)
    }
  }
  return map
})

const graphEdges = computed(() => {
  const dedup = new Map<string, CampusGraphEdgeVO>()
  for (const edge of props.graph?.edges || []) {
    if (edge.fromNodeId === undefined || edge.toNodeId === undefined) {
      continue
    }
    const from = Number(edge.fromNodeId)
    const to = Number(edge.toNodeId)
    const key = from < to ? `${from}-${to}` : `${to}-${from}`
    const exists = dedup.get(key)
    if (!exists || Number(edge.highlight || 0) > Number(exists.highlight || 0)) {
      dedup.set(key, edge)
    }
  }

  const placedLabelRects: Rect[] = []
  const nodeHitRects = graphNodes.value.map((node) => toRect(node.x, node.y, 28, 28))
  const nodeLabelRects = nodeLabelPlacements.value.map((label) => ({
    x1: label.bgX,
    y1: label.bgY,
    x2: label.bgX + label.bgW,
    y2: label.bgY + label.bgH,
  }))

  return Array.from(dedup.values())
    .map((edge) => {
      const fromNode = graphNodeMapWithCoord.value.get(Number(edge.fromNodeId))
      const toNode = graphNodeMapWithCoord.value.get(Number(edge.toNodeId))
      if (!fromNode || !toNode) {
        return undefined
      }
      const mx = (fromNode.x + toNode.x) / 2
      const my = (fromNode.y + toNode.y) / 2
      const dx = toNode.x - fromNode.x
      const dy = toNode.y - fromNode.y
      const len = Math.max(1, Math.sqrt(dx * dx + dy * dy))
      const nx = -dy / len
      const ny = dx / len
      const tx = dx / len
      const ty = dy / len
      const shifts = [-30, -16, 16, 30, 0, -40, 40]
      let labelX = mx
      let labelY = my
      let bestScore = Number.MAX_VALUE
      for (let i = 0; i < shifts.length; i++) {
        const side = shifts[i]
        const tangent = i % 2 === 0 ? 8 : -8
        const cx = mx + nx * side + tx * tangent
        const cy = my + ny * side + ty * tangent
        const rect = toRect(cx, cy, 92, 24)
        let score = i * 0.01
        for (const r of placedLabelRects) {
          score += overlapArea(rect, r) * 1.2
        }
        for (const r of nodeHitRects) {
          score += overlapArea(rect, r) * 2
        }
        for (const r of nodeLabelRects) {
          score += overlapArea(rect, r) * 1.5
        }
        if (score < bestScore) {
          bestScore = score
          labelX = cx
          labelY = cy
        }
      }
      placedLabelRects.push(toRect(labelX, labelY, 92, 24))

      return {
        ...edge,
        x1: fromNode.x,
        y1: fromNode.y,
        x2: toNode.x,
        y2: toNode.y,
        lx: labelX,
        ly: labelY,
      }
    })
    .filter(Boolean) as Array<CampusGraphEdgeVO & {
      x1: number
      y1: number
      x2: number
      y2: number
      lx: number
      ly: number
    }>
})

const routeNodeSet = computed(() => {
  const set = new Set<number>()
  for (const id of props.graph?.routeNodeIds || []) {
    set.add(Number(id))
  }
  return set
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
    const fromNode = graphNodeMapWithCoord.value.get(fromId)
    const toNode = graphNodeMapWithCoord.value.get(toId)
    if (!fromNode || !toNode) {
      continue
    }
    const cost = edgeCostMap.value.get(`${fromId}-${toId}`) ?? edgeCostMap.value.get(`${toId}-${fromId}`) ?? 60
    const costSec = Math.max(1, Number(cost))
    segments.push({
      fromNode,
      toNode,
      costSec,
      startSec: acc,
      endSec: acc + costSec,
    })
    acc += costSec
  }

  return { segments, totalSec: acc }
})

const focusRiderId = computed(() => {
  if (props.graph?.riderId !== undefined && props.graph.riderId !== null) {
    return Number(props.graph.riderId)
  }
  const highlighted = (props.graph?.riders || []).find((rider) => Number(rider.highlight || 0) === 1)
  return highlighted?.riderId === undefined ? -1 : Number(highlighted.riderId)
})

const syncedAtClientMs = ref(Date.now())
const syncedElapsedSec = ref(0)
const tickMs = ref(Date.now())
const selectedRiderId = ref<number | null>(null)
let tickTimer: ReturnType<typeof setInterval> | undefined

watch(
  () => [props.graph?.dispatchStartTimeMs, props.graph?.serverTimeMs, props.graph?.dispatchStatus, props.graph?.orderId],
  () => {
    syncedAtClientMs.value = Date.now()
    const startMs = Number(props.graph?.dispatchStartTimeMs || 0)
    const serverMs = Number(props.graph?.serverTimeMs || 0)
    if (Number(props.graph?.dispatchStatus) === 1 && startMs > 0 && serverMs >= startMs) {
      syncedElapsedSec.value = (serverMs - startMs) / 1000
    } else {
      syncedElapsedSec.value = 0
    }
  },
  { immediate: true }
)

watch(
  () => props.graph?.orderId,
  () => {
    selectedRiderId.value = null
    resetView()
  },
  { immediate: true }
)

onMounted(() => {
  tickTimer = setInterval(() => {
    tickMs.value = Date.now()
  }, 200)
  window.addEventListener('mousemove', onWindowMouseMove)
  window.addEventListener('mouseup', onWindowMouseUp)
})

onBeforeUnmount(() => {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = undefined
  }
  window.removeEventListener('mousemove', onWindowMouseMove)
  window.removeEventListener('mouseup', onWindowMouseUp)
})

const liveElapsedSec = computed(() => {
  if (Number(props.graph?.dispatchStatus) !== 1) {
    return syncedElapsedSec.value
  }
  return Math.max(0, syncedElapsedSec.value + (tickMs.value - syncedAtClientMs.value) / 1000)
})

const dynamicMainRider = computed(() => {
  if (Number(props.graph?.dispatchStatus) !== 1) {
    return undefined
  }
  const riderId = focusRiderId.value
  if (riderId < 0) {
    return undefined
  }
  const rider = (props.graph?.riders || []).find((item) => Number(item.riderId) === riderId)
  if (!rider) {
    return undefined
  }
  const metrics = routeMetrics.value
  if (!metrics.segments.length) {
    return undefined
  }

  const elapsed = liveElapsedSec.value
  if (elapsed <= 0) {
    const first = metrics.segments[0]
    return {
      rider,
      x: first.fromNode.x,
      y: first.fromNode.y,
      rotateDeg: Math.atan2(first.toNode.y - first.fromNode.y, first.toNode.x - first.fromNode.x) * 180 / Math.PI,
      nodeName: first.fromNode.name,
    }
  }

  if (elapsed >= metrics.totalSec) {
    const last = metrics.segments[metrics.segments.length - 1]
    return {
      rider,
      x: last.toNode.x,
      y: last.toNode.y,
      rotateDeg: Math.atan2(last.toNode.y - last.fromNode.y, last.toNode.x - last.fromNode.x) * 180 / Math.PI,
      nodeName: last.toNode.name,
    }
  }

  const segment = metrics.segments.find((item) => elapsed < item.endSec) || metrics.segments[metrics.segments.length - 1]
  const ratio = Math.max(0, Math.min(1, (elapsed - segment.startSec) / segment.costSec))
  const x = segment.fromNode.x + (segment.toNode.x - segment.fromNode.x) * ratio
  const y = segment.fromNode.y + (segment.toNode.y - segment.fromNode.y) * ratio
  const rotateDeg = Math.atan2(segment.toNode.y - segment.fromNode.y, segment.toNode.x - segment.fromNode.x) * 180 / Math.PI
  const nodeName = ratio >= 0.5 ? segment.toNode.name : segment.fromNode.name
  return { rider, x, y, rotateDeg, nodeName }
})

const riderMarkers = computed<RiderMarker[]>(() => {
  const riders = props.graph?.riders || []
  const markers: RiderMarker[] = []
  const dynamic = dynamicMainRider.value
  const dynamicRiderId = dynamic ? Number(dynamic.rider.riderId) : -1

  if (dynamic) {
    markers.push({
      ...dynamic.rider,
      x: dynamic.x,
      y: dynamic.y,
      nodeName: dynamic.nodeName,
      badge: String(dynamic.rider.riderId ?? ''),
      rotateDeg: dynamic.rotateDeg,
      isMoving: true,
      highlight: 1,
    })
  }

  const grouped = new Map<number, CampusRiderVO[]>()
  for (const rider of riders) {
    if (Number(rider.riderId) === dynamicRiderId) {
      continue
    }
    const nodeId = Number(rider.currentNodeId)
    if (!grouped.has(nodeId)) {
      grouped.set(nodeId, [])
    }
    grouped.get(nodeId)!.push(rider)
  }

  const offsets = [
    { dx: -22, dy: 16 },
    { dx: -30, dy: 4 },
    { dx: -14, dy: 28 },
    { dx: -38, dy: 20 },
    { dx: -8, dy: 40 },
    { dx: -28, dy: -10 },
    { dx: -18, dy: -20 },
    { dx: -40, dy: -2 },
  ]

  for (const [nodeId, list] of grouped.entries()) {
    const node = graphNodeMapWithCoord.value.get(nodeId)
    if (!node) {
      continue
    }
    list
      .slice()
      .sort((a, b) => {
        const h = Number(b.highlight || 0) - Number(a.highlight || 0)
        return h !== 0 ? h : Number(a.riderId || 0) - Number(b.riderId || 0)
      })
      .forEach((rider, idx) => {
        const base = offsets[idx % offsets.length]
        const ring = Math.floor(idx / offsets.length)
        const spread = ring * 8
        markers.push({
          ...rider,
          x: node.x + base.dx - spread,
          y: node.y + base.dy + spread * 0.5,
          nodeName: node.name,
          badge: String(rider.riderId ?? ''),
          rotateDeg: 0,
          isMoving: false,
        })
      })
  }

  return markers.sort((a, b) => Number(b.highlight || 0) - Number(a.highlight || 0))
})

const clearSelectedRider = () => {
  selectedRiderId.value = null
}

const selectRider = (rider: RiderMarker) => {
  const nextId = Number(rider.riderId ?? -1)
  if (nextId < 0) {
    return
  }
  selectedRiderId.value = nextId
}

watch(riderMarkers, (markers) => {
  if (selectedRiderId.value === null) {
    return
  }
  const exists = markers.some((item) => Number(item.riderId) === selectedRiderId.value)
  if (!exists) {
    selectedRiderId.value = null
  }
}, { deep: true })

const selectedRiderPopup = computed<RiderPopup | null>(() => {
  if (selectedRiderId.value === null) {
    return null
  }
  const rider = riderMarkers.value.find((item) => Number(item.riderId) === selectedRiderId.value)
  if (!rider) {
    return null
  }
  const width = 226
  const height = 92
  let x = rider.x + 22
  let y = rider.y - 86
  if (x + width > viewWidth - 8) {
    x = rider.x - width - 16
  }
  if (y < 8) {
    y = rider.y + 16
  }
  x = Math.max(8, Math.min(x, viewWidth - width - 8))
  y = Math.max(8, Math.min(y, viewHeight - height - 8))
  const title = `${rider.riderName || '骑手'}（${rider.badge}）`
  const phone = rider.riderPhone || '-'
  const loadText = `载单：${rider.activeLoad ?? 0}`
  const nodeText = `位置：${rider.nodeName || rider.currentNodeId || '-'}`
  const motionText = `状态：${rider.isMoving ? '行进中' : '待命/驻留'}`
  return { x, y, width, height, title, phone, loadText, nodeText, motionText }
})

const nodeBodyColor = (nodeType?: string) => {
  if (nodeType === 'SHOP') {
    return '#fdba74'
  }
  if (nodeType === 'DROPOFF') {
    return '#93c5fd'
  }
  return '#cbd5e1'
}

const nodeRoofColor = (nodeType?: string) => {
  if (nodeType === 'SHOP') {
    return '#f97316'
  }
  if (nodeType === 'DROPOFF') {
    return '#2563eb'
  }
  return '#64748b'
}

const edgeLabel = (edge: CampusGraphEdgeVO) => `${edge.distanceM || 0}m / ${edge.costTimeSec || 0}s`

const routeText = computed(() => {
  if (props.graph?.routeText) {
    return props.graph.routeText
  }
  const names = (props.graph?.routeNodeIds || []).map((id) => nodeMap.value.get(Number(id))?.name || `节点${id}`)
  return names.join(' -> ')
})
</script>

<template>
  <div class="campus-graph-panel">
    <template v-if="hasGraphData">
      <div class="graph-stage">
      <svg
        ref="svgRef"
        class="campus-graph-svg"
        :class="{ dragging: isDragging }"
        :style="{ height: `${height}px` }"
        :viewBox="`0 0 ${viewWidth} ${viewHeight}`"
        @wheel.prevent="onWheel"
        @mousedown="onMouseDown"
        @click="clearSelectedRider"
      >
        <g :transform="contentTransform">
        <g>
          <line
            v-for="(edge, index) in graphEdges"
            :key="`line-${index}`"
            :x1="edge.x1"
            :y1="edge.y1"
            :x2="edge.x2"
            :y2="edge.y2"
            :class="['edge-line', edge.highlight ? 'edge-line-highlight edge-flow' : '']"
          />
          <g v-for="(edge, index) in graphEdges" :key="`label-${index}`">
            <rect :x="edge.lx - 46" :y="edge.ly - 12" width="92" height="22" rx="7" ry="7" class="edge-label-bg" />
            <text :x="edge.lx" :y="edge.ly + 4" class="edge-label-text">{{ edgeLabel(edge) }}</text>
          </g>
        </g>

        <g>
          <g v-for="(node, index) in graphNodes" :key="`node-${index}`">
            <circle
              v-if="routeNodeSet.has(Number(node.nodeId))"
              :cx="node.x"
              :cy="node.y"
              r="13"
              class="node-halo"
            />
            <g :transform="`translate(${node.x},${node.y})`" class="node-house">
              <path d="M -9 -1 L 0 -10 L 9 -1 Z" :fill="nodeRoofColor(node.nodeType)" class="node-house-stroke" />
              <rect x="-7.5" y="-1" width="15" height="11" rx="2" :fill="nodeBodyColor(node.nodeType)" class="node-house-stroke" />
              <rect x="-2.2" y="4" width="4.4" height="6" rx="1" class="node-house-door" />
              <rect x="-5.9" y="1.4" width="3.2" height="2.4" rx="0.8" class="node-house-window" />
              <rect x="2.7" y="1.4" width="3.2" height="2.4" rx="0.8" class="node-house-window" />
            </g>

            <rect
              v-if="nodeLabelMap.get(Number(node.nodeId))"
              :x="nodeLabelMap.get(Number(node.nodeId))!.bgX"
              :y="nodeLabelMap.get(Number(node.nodeId))!.bgY"
              :width="nodeLabelMap.get(Number(node.nodeId))!.bgW"
              :height="nodeLabelMap.get(Number(node.nodeId))!.bgH"
              rx="8"
              ry="8"
              class="node-label-bg"
            />
            <text
              v-if="nodeLabelMap.get(Number(node.nodeId))"
              :x="nodeLabelMap.get(Number(node.nodeId))!.x"
              :y="nodeLabelMap.get(Number(node.nodeId))!.y"
              class="node-label"
              :text-anchor="nodeLabelMap.get(Number(node.nodeId))!.textAnchor"
            >
              {{ nodeLabelMap.get(Number(node.nodeId))!.name }}
            </text>
          </g>
        </g>

        <g>
          <g
            v-for="(rider, index) in riderMarkers"
            :key="`rider-${index}`"
            class="rider-group"
            :class="{ selected: Number(rider.riderId) === selectedRiderId }"
            @click.stop="selectRider(rider)"
          >
            <ellipse :cx="rider.x" :cy="rider.y + 10" :rx="rider.isMoving ? 12 : 10" ry="3.4" class="rider-shadow" />
            <g :transform="`translate(${rider.x},${rider.y}) rotate(${rider.rotateDeg})`" :class="['rider-moto', rider.highlight ? 'rider-moto-highlight' : '']">
              <circle cx="-9.2" cy="6.8" r="3.9" class="moto-wheel" />
              <circle cx="9.5" cy="6.8" r="3.9" class="moto-wheel" />
              <path d="M -12 2.8 L -7 -1.8 L 5.8 -1.8 Q 8.8 -1.8 10.8 0.7 L 13.2 3.6 L 3.5 3.6 Q 1.8 3.6 0.9 4.4 L -1.4 6.4 L -9.8 6.4 Z" class="moto-body" />
              <path d="M -8.2 -1.8 L -3.4 -7.1 L -1.4 -7.1 L -2.3 -2.1" class="moto-windshield" />
              <path d="M -7.5 5.1 L -2.8 5.1 L 0.8 2.1 L 7.8 2.1 L 12.8 5.1" class="moto-frame" />
              <rect x="4.9" y="-5.1" width="5.6" height="3.5" rx="1.1" class="moto-box" />
              <circle cx="13.6" cy="-0.4" r="1.4" class="moto-head" />
            </g>
            <g :transform="`translate(${rider.x + 16},${rider.y - 12})`">
              <circle r="7.4" :class="['rider-id-dot', rider.highlight ? 'rider-id-dot-hi' : '']" />
              <text y="3" class="rider-id-text">{{ rider.badge }}</text>
            </g>
          </g>
        </g>

        <g v-if="selectedRiderPopup" class="rider-popup" @click.stop>
          <rect
            :x="selectedRiderPopup.x"
            :y="selectedRiderPopup.y"
            :width="selectedRiderPopup.width"
            :height="selectedRiderPopup.height"
            rx="9"
            ry="9"
            class="rider-popup-bg"
          />
          <text :x="selectedRiderPopup.x + 12" :y="selectedRiderPopup.y + 18" class="rider-popup-title">{{ selectedRiderPopup.title }}</text>
          <text :x="selectedRiderPopup.x + 12" :y="selectedRiderPopup.y + 36" class="rider-popup-line">电话：{{ selectedRiderPopup.phone }}</text>
          <text :x="selectedRiderPopup.x + 12" :y="selectedRiderPopup.y + 52" class="rider-popup-line">{{ selectedRiderPopup.loadText }}</text>
          <text :x="selectedRiderPopup.x + 12" :y="selectedRiderPopup.y + 68" class="rider-popup-line">{{ selectedRiderPopup.nodeText }}</text>
          <text :x="selectedRiderPopup.x + 12" :y="selectedRiderPopup.y + 84" class="rider-popup-line">{{ selectedRiderPopup.motionText }}</text>
        </g>
        </g>
      </svg>
      <div class="graph-tools">
        <button type="button" class="tool-btn" @click="zoomIn">+</button>
        <button type="button" class="tool-btn" @click="zoomOut">-</button>
        <button type="button" class="tool-btn reset" @click="resetView">复位</button>
        <span class="zoom-text">{{ zoomPercentText }}</span>
      </div>
      </div>

      <div class="campus-legend">
        <span class="legend-item"><i class="legend-house"></i>节点（小房子）</span>
        <span class="legend-item"><i class="legend-moto"></i>骑手（小摩托）</span>
        <span class="legend-item"><i class="legend-line"></i>普通边</span>
        <span class="legend-item"><i class="legend-line hi"></i>订单高亮路径</span>
      </div>
      <div class="route-text">当前路线：{{ routeText || '-' }}</div>
      <div class="rider-list" v-if="riderMarkers.length">
        <div class="rider-item" v-for="item in riderMarkers" :key="`rider-info-${item.riderId}`" :class="{ active: item.highlight === 1 }">
          <span class="badge">{{ item.badge }}</span>
          <span>{{ item.riderName || '骑手' }}（载单{{ item.activeLoad ?? 0 }}） @ {{ item.nodeName || item.currentNodeId || '-' }}</span>
        </div>
      </div>
    </template>
    <div v-else class="campus-empty">暂无路网数据</div>
  </div>
</template>

<style scoped lang="less">
.campus-graph-panel {
  width: 100%;
  border: 1px solid #d7e9ee;
  border-radius: 8px;
  background: linear-gradient(180deg, #fafdff 0%, #f6fbff 100%);
  padding: 12px;

  .graph-stage {
    position: relative;
  }

  .campus-graph-svg {
    width: 100%;
    display: block;
    border: 1px solid #e7eef2;
    border-radius: 8px;
    background: radial-gradient(circle at 20% 10%, #f8fcff 0%, #f2f8ff 52%, #edf4ff 100%);
    cursor: grab;
    user-select: none;
  }

  .campus-graph-svg.dragging {
    cursor: grabbing;
  }

  .graph-tools {
    position: absolute;
    top: 10px;
    right: 10px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 6px 8px;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.92);
    border: 1px solid #dbeafe;
    box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  }

  .tool-btn {
    min-width: 26px;
    height: 24px;
    border: 1px solid #cfe0ff;
    background: #ffffff;
    color: #1e40af;
    border-radius: 6px;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    line-height: 1;
  }

  .tool-btn:hover {
    background: #f3f8ff;
  }

  .tool-btn.reset {
    min-width: 38px;
    font-size: 11px;
  }

  .zoom-text {
    font-size: 11px;
    color: #334155;
    min-width: 42px;
    text-align: right;
  }

  .edge-line {
    stroke: #9ca3af;
    stroke-width: 2.1;
  }

  .edge-line-highlight {
    stroke: #0ea5e9;
    stroke-width: 3.2;
    stroke-dasharray: 10 8;
  }

  .edge-flow {
    animation: edge-flow 1.8s linear infinite;
  }

  .edge-label-bg {
    fill: rgba(255, 255, 255, 0.88);
    stroke: #d6e4ff;
    stroke-width: 1;
  }

  .edge-label-text {
    font-size: 12px;
    fill: #334155;
    text-anchor: middle;
    font-weight: 600;
  }

  .node-halo {
    fill: rgba(14, 165, 233, 0.15);
    stroke: rgba(14, 165, 233, 0.45);
    stroke-width: 1.4;
  }

  .node-house-stroke {
    stroke: #ffffff;
    stroke-width: 1;
  }

  .node-house-door {
    fill: rgba(255, 255, 255, 0.92);
  }

  .node-house-window {
    fill: rgba(255, 255, 255, 0.88);
  }

  .node-label {
    font-size: 12px;
    fill: #0f172a;
    font-weight: 600;
  }

  .node-label-bg {
    fill: rgba(255, 255, 255, 0.94);
    stroke: #cfe0ff;
    stroke-width: 1;
  }

  .rider-shadow {
    fill: rgba(100, 116, 139, 0.2);
  }

  .rider-group {
    cursor: pointer;
  }

  .rider-group.selected .rider-shadow {
    fill: rgba(245, 158, 11, 0.35);
  }

  .rider-moto .moto-wheel {
    fill: #1f2937;
    stroke: #e2e8f0;
    stroke-width: 0.9;
  }

  .rider-moto .moto-body {
    fill: #0ea5e9;
    stroke: #0c4a6e;
    stroke-width: 0.9;
  }

  .rider-moto .moto-frame {
    fill: none;
    stroke: #0f172a;
    stroke-width: 1.5;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .rider-moto .moto-windshield {
    fill: none;
    stroke: #38bdf8;
    stroke-width: 1.1;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  .rider-moto .moto-box {
    fill: #38bdf8;
    stroke: #0c4a6e;
    stroke-width: 0.8;
  }

  .rider-moto .moto-head {
    fill: #fef08a;
    stroke: #b45309;
    stroke-width: 0.8;
  }

  .rider-moto-highlight .moto-body {
    fill: #f59e0b;
    stroke: #92400e;
  }

  .rider-moto-highlight .moto-frame {
    stroke: #92400e;
  }

  .rider-moto-highlight .moto-windshield {
    stroke: #fef3c7;
  }

  .rider-moto-highlight .moto-box {
    fill: #fbbf24;
    stroke: #92400e;
  }

  .rider-moto-highlight .moto-head {
    fill: #fbbf24;
    stroke: #92400e;
  }

  .rider-id-dot {
    fill: #e2f2ff;
    stroke: #0ea5e9;
    stroke-width: 1.1;
  }

  .rider-id-dot-hi {
    fill: #fef3c7;
    stroke: #f59e0b;
  }

  .rider-id-text {
    text-anchor: middle;
    font-size: 8.5px;
    fill: #0f172a;
    font-weight: 700;
  }

  .rider-popup-bg {
    fill: rgba(255, 255, 255, 0.96);
    stroke: #bae6fd;
    stroke-width: 1;
    filter: drop-shadow(0 4px 10px rgba(15, 23, 42, 0.12));
  }

  .rider-popup-title {
    font-size: 12px;
    font-weight: 700;
    fill: #0f172a;
  }

  .rider-popup-line {
    font-size: 11px;
    fill: #334155;
  }

  .campus-legend {
    display: flex;
    flex-wrap: wrap;
    gap: 8px 14px;
    margin-top: 10px;
  }

  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: #4b5563;
    font-size: 12px;
  }

  .legend-house {
    width: 11px;
    height: 11px;
    display: inline-block;
    background: linear-gradient(180deg, #f97316 0%, #fdba74 100%);
    clip-path: polygon(50% 0%, 100% 38%, 100% 100%, 0 100%, 0 38%);
  }

  .legend-moto {
    width: 14px;
    height: 8px;
    border-radius: 4px;
    display: inline-block;
    background: linear-gradient(90deg, #10b981 0%, #34d399 100%);
  }

  .legend-line {
    width: 18px;
    height: 0;
    border-top: 2px solid #9ca3af;
    display: inline-block;
  }

  .legend-line.hi {
    border-top-color: #0ea5e9;
    border-top-width: 3px;
  }

  .route-text {
    margin-top: 8px;
    font-size: 12px;
    color: #374151;
    line-height: 1.6;
  }

  .rider-list {
    margin-top: 8px;
    display: flex;
    flex-wrap: wrap;
    gap: 6px 12px;
  }

  .rider-item {
    font-size: 12px;
    color: #374151;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .rider-item.active {
    color: #0369a1;
    font-weight: 600;
  }

  .rider-item .badge {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: #0ea5e9;
    color: #ffffff;
    font-size: 10px;
    font-weight: 700;
  }

  .campus-empty {
    height: 180px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #888;
    font-size: 13px;
  }
}

@keyframes edge-flow {
  from {
    stroke-dashoffset: 36;
  }
  to {
    stroke-dashoffset: 0;
  }
}
</style>
