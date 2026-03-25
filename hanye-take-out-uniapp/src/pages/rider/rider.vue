<template>
  <view class="page">
    <view class="white_box">
      <view class="tool_row">
        <view class="picker_wrap">
          <text class="label">骑手：</text>
          <picker
            mode="selector"
            :range="riderOptions"
            range-key="label"
            :value="selectedRiderIndex"
            @change="onSelectRider"
          >
            <view class="picker_value">{{ selectedRiderLabel }}</view>
          </picker>
        </view>
        <view class="refresh_btn" @click="refreshGraph">刷新</view>
      </view>
    </view>

    <view class="white_box">
      <campus-graph-canvas title="骑手调度路网" :graph="graph" :height-rpx="520" />
    </view>

    <view class="white_box">
      <view class="info_title">骑手详情</view>
      <view class="info_row"><text class="k">姓名：</text><text class="v">{{ activeRider?.riderName || '-' }}</text></view>
      <view class="info_row"><text class="k">电话：</text><text class="v">{{ activeRider?.riderPhone || '-' }}</text></view>
      <view class="info_row"><text class="k">当前载单：</text><text class="v">{{ activeRider?.activeLoad ?? '-' }}</text></view>
      <view class="info_row"><text class="k">当前位置：</text><text class="v">{{ currentNodeName }}</text></view>
      <view class="info_row">
        <text class="k">高亮路线：</text>
        <text class="v route">{{ graph?.routeText || '暂无任务路线' }}</text>
      </view>
    </view>
  </view>
</template>

<script lang="ts" setup>
import CampusGraphCanvas from '@/components/campus/CampusGraphCanvas.vue'
import {computed, ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {getCampusGraphAPI, getCampusGraphByRiderAPI} from '@/api/order'
import type {CampusGraphVO, CampusRiderVO} from '@/types/order'

type RiderOption = {
  riderId: number
  label: string
}

const graph = ref<CampusGraphVO>()
const riderOptions = ref<RiderOption[]>([])
const selectedRiderId = ref<number>()

const selectedRiderIndex = computed(() => {
  const idx = riderOptions.value.findIndex((item) => item.riderId === selectedRiderId.value)
  return idx >= 0 ? idx : 0
})

const selectedRiderLabel = computed(() => {
  const current = riderOptions.value[selectedRiderIndex.value]
  return current?.label || '暂无骑手数据'
})

const activeRider = computed<CampusRiderVO | undefined>(() => {
  return (graph.value?.riders || []).find((item) => item.riderId === selectedRiderId.value)
})

const currentNodeName = computed(() => {
  const nodeId = activeRider.value?.currentNodeId
  if (nodeId === undefined) {
    return '-'
  }
  return (graph.value?.nodes || []).find((node) => node.nodeId === nodeId)?.name || `节点${nodeId}`
})

const loadGraph = async () => {
  const baseRes = await getCampusGraphAPI()
  if (baseRes.code !== 0) {
    return
  }
  const baseGraph = baseRes.data
  const riders = (baseGraph.riders || []).map((rider) => ({
    riderId: Number(rider.riderId),
    label: `${rider.riderName || '骑手'}（载单${rider.activeLoad ?? 0}）`,
  }))
  riderOptions.value = riders
  if (!riders.length) {
    graph.value = baseGraph
    selectedRiderId.value = undefined
    return
  }
  if (!selectedRiderId.value || !riders.some((item) => item.riderId === selectedRiderId.value)) {
    const highlighted = (baseGraph.riders || []).find((item) => item.highlight === 1)
    selectedRiderId.value = highlighted?.riderId || riders[0].riderId
  }
  await loadGraphByRider(selectedRiderId.value as number)
}

const loadGraphByRider = async (riderId: number) => {
  const res = await getCampusGraphByRiderAPI(riderId)
  if (res.code === 0) {
    graph.value = res.data
  }
}

const onSelectRider = async (event: any) => {
  const idx = Number(event.detail.value)
  const target = riderOptions.value[idx]
  if (!target) return
  selectedRiderId.value = target.riderId
  await loadGraphByRider(target.riderId)
}

const refreshGraph = async () => {
  if (selectedRiderId.value) {
    await loadGraphByRider(selectedRiderId.value)
    return
  }
  await loadGraph()
}

onShow(async () => {
  await loadGraph()
})
</script>

<style lang="less" scoped>
.page {
  padding-bottom: 20rpx;
}

.white_box {
  margin: 20rpx;
  background-color: #fff;
  border-radius: 20rpx;
  padding: 20rpx;
}

.tool_row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.picker_wrap {
  display: flex;
  align-items: center;
  width: 76%;

  .label {
    font-size: 30rpx;
    color: #333;
    margin-right: 8rpx;
  }

  .picker_value {
    width: 100%;
    font-size: 28rpx;
    color: #333;
    border: 1px solid #e5e7eb;
    border-radius: 10rpx;
    padding: 12rpx 16rpx;
    background: #f8fbff;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.refresh_btn {
  width: 120rpx;
  text-align: center;
  line-height: 60rpx;
  font-size: 28rpx;
  color: #fff;
  border-radius: 30rpx;
  background: #0ea5e9;
}

.info_title {
  font-size: 30rpx;
  color: #111827;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.info_row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 10rpx;

  .k {
    width: 170rpx;
    font-size: 26rpx;
    color: #6b7280;
    line-height: 36rpx;
  }

  .v {
    flex: 1;
    font-size: 26rpx;
    color: #111827;
    line-height: 36rpx;
  }

  .route {
    color: #0369a1;
  }
}
</style>
