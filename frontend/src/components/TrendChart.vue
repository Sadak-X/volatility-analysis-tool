<template>
  <v-chart :option="option" autoresize style="height: 340px" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, LineChart, GridComponent, TooltipComponent, LegendComponent])

const props = defineProps<{
  series: Array<{ date: string; value?: number; impliedValue?: number | null; predValue?: number; ciLower?: number; ciUpper?: number }>
  comparisonSeries?: Array<{
    name: string
    data: Array<{ date: string; value?: number; impliedValue?: number | null; predValue?: number; ciLower?: number; ciUpper?: number }>
  }>
}>()

const hasComparisonSeries = computed(() => Boolean(props.comparisonSeries?.length))
const hasImpliedSeries = computed(() => !hasComparisonSeries.value && props.series.some((item) => item.impliedValue != null))
const hasPredictionBounds = computed(
  () => !hasComparisonSeries.value && props.series.some((item) => item.ciLower != null || item.ciUpper != null),
)
const palette = ['#0f6cbd', '#149e61', '#d4380d', '#7132f5', '#f59e0b', '#0891b2', '#be123c', '#4b5563']

const xAxisData = computed(() => {
  if (!hasComparisonSeries.value) {
    return props.series.map((item) => item.date)
  }
  const dates = new Set<string>()
  props.comparisonSeries?.forEach((series) => {
    series.data.forEach((item) => dates.add(item.date))
  })
  return Array.from(dates).sort()
})

const chartSeries = computed(() => {
  if (hasComparisonSeries.value) {
    return (props.comparisonSeries || []).map((item, index) => {
      const dataByDate = new Map(item.data.map((point) => [point.date, point.value ?? point.predValue ?? null]))
      return {
        name: item.name,
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: xAxisData.value.map((date) => dataByDate.get(date) ?? null),
        lineStyle: { width: 3, color: palette[index % palette.length] },
      }
    })
  }

  const series: any[] = [
    {
      name: hasImpliedSeries.value ? '历史波动率' : '波动率',
      type: 'line',
      smooth: true,
      data: props.series.map((item) => item.value ?? item.predValue ?? 0),
      lineStyle: { width: 3, color: '#0f6cbd' },
      areaStyle: { color: 'rgba(15,108,189,0.12)' },
    },
  ]

  if (hasImpliedSeries.value) {
    series.push({
      name: '隐含波动率',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: props.series.map((item) => item.impliedValue ?? null),
      lineStyle: { width: 2, color: '#f59e0b' },
    })
  }

  if (hasPredictionBounds.value) {
    series.push(
      {
        name: '下界',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: props.series.map((item) => item.ciLower ?? null),
        lineStyle: { type: 'dashed', color: '#f59e0b' },
      },
      {
        name: '上界',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: props.series.map((item) => item.ciUpper ?? null),
        lineStyle: { type: 'dashed', color: '#ef4444' },
      },
    )
  }

  return series
})

const option = computed(() => ({
  tooltip: {
    trigger: 'axis',
    valueFormatter: (value: number | string) => {
      if (value == null || value === '-') return '-'
      return `${(Number(value) * 100).toFixed(2)}%`
    },
  },
  legend: { top: 0 },
  grid: { left: 48, right: 24, top: 48, bottom: 28 },
  xAxis: {
    type: 'category',
    data: xAxisData.value,
  },
  yAxis: {
    type: 'value',
    axisLabel: { formatter: (value: number) => `${(value * 100).toFixed(0)}%` },
  },
  series: chartSeries.value,
}))
</script>
