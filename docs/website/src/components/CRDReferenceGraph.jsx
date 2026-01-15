import { useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  Position,
  useNodesState,
  useEdgesState,
  Handle,
} from '@xyflow/react'
import dagre from 'dagre'
import '@xyflow/react/dist/style.css'

// CRD definitions with their colors and references
const crdDefinitions = [
  {
    id: 'ApplicationService',
    label: 'ApplicationService',
    color: '#3B82F6',
    description: 'Root resource (tenant)',
    refs: [],
  },
  {
    id: 'KafkaCluster',
    label: 'KafkaCluster',
    color: '#8B5CF6',
    description: 'Virtual Kafka cluster',
    refs: [{ target: 'ApplicationService', label: 'applicationServiceRef', required: true }],
  },
  {
    id: 'ServiceAccount',
    label: 'ServiceAccount',
    color: '#10B981',
    description: 'Client identity',
    refs: [
      { target: 'KafkaCluster', label: 'clusterRef', required: true },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
  {
    id: 'Topic',
    label: 'Topic',
    color: '#F59E0B',
    description: 'Kafka topic',
    refs: [
      { target: 'ServiceAccount', label: 'serviceRef', required: true },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
  {
    id: 'ConsumerGroup',
    label: 'ConsumerGroup',
    color: '#6B7280',
    description: 'Consumer group',
    refs: [
      { target: 'ServiceAccount', label: 'serviceRef', required: true },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
  {
    id: 'ACL',
    label: 'ACL',
    color: '#EF4444',
    description: 'Access control',
    refs: [
      { target: 'ServiceAccount', label: 'serviceRef', required: true },
      { target: 'Topic', label: 'topicRef', required: false },
      { target: 'ConsumerGroup', label: 'consumerGroupRef', required: false },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
]

// Custom node with visible anchor points (white circles with colored border)
function CRDNode({ data }) {
  const { targetHandles = [], sourceHandles = [], label, color } = data
  const isACL = label === 'ACL'

  const sourcePosition = isACL ? Position.Top : Position.Bottom
  const targetPosition = Position.Bottom

  // Visible anchor point style: white fill with colored border
  const handleStyle = {
    width: 10,
    height: 10,
    background: 'white',
    border: `2px solid ${color}`,
    boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
  }

  return (
    <div
      style={{
        padding: '12px 16px',
        borderRadius: '8px',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
        border: `2px solid ${color}`,
        backgroundColor: color,
        minWidth: '160px',
        position: 'relative',
      }}
    >
      {/* Target handles - visible anchor points at bottom */}
      {targetHandles.map((h) => (
        <Handle
          key={h.id}
          type="target"
          position={targetPosition}
          id={h.id}
          style={{ ...handleStyle, left: h.position }}
        />
      ))}

      <div style={{ color: 'white', fontWeight: 'bold', fontSize: '14px', textAlign: 'center' }}>
        {label}
      </div>
      <div style={{ color: 'rgba(255,255,255,0.7)', fontSize: '11px', textAlign: 'center', marginTop: '4px' }}>
        {data.description}
      </div>

      {/* Source handles - visible anchor points */}
      {sourceHandles.map((h) => (
        <Handle
          key={h.id}
          type="source"
          position={sourcePosition}
          id={h.id}
          style={{ ...handleStyle, left: h.position }}
        />
      ))}
    </div>
  )
}

// Build orthogonal path with 90° angles and small rounded corners (4px)
function buildOrthogonalPath(sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, offset = 0) {
  const borderRadius = 4
  const segments = []

  // Determine if we need horizontal offset for parallel line spacing
  const horizontalOffset = offset * 15

  if (sourcePosition === Position.Top && targetPosition === Position.Bottom) {
    // Source at top going up to target at bottom (child → parent)
    const midY = sourceY - (sourceY - targetY - 40) / 2

    // Start point
    const startX = sourceX + horizontalOffset
    const startY = sourceY

    // End point (target bottom)
    const endX = targetX + horizontalOffset
    const endY = targetY + 40

    // Build path with rounded corners
    segments.push(`M ${startX} ${startY}`)

    if (Math.abs(startX - endX) < 5) {
      // Straight vertical line
      segments.push(`L ${endX} ${endY}`)
    } else {
      // Orthogonal with corners
      segments.push(`L ${startX} ${midY + borderRadius}`)

      if (startX < endX) {
        segments.push(`Q ${startX} ${midY} ${startX + borderRadius} ${midY}`)
        segments.push(`L ${endX - borderRadius} ${midY}`)
        segments.push(`Q ${endX} ${midY} ${endX} ${midY - borderRadius}`)
      } else {
        segments.push(`Q ${startX} ${midY} ${startX - borderRadius} ${midY}`)
        segments.push(`L ${endX + borderRadius} ${midY}`)
        segments.push(`Q ${endX} ${midY} ${endX} ${midY - borderRadius}`)
      }

      segments.push(`L ${endX} ${endY}`)
    }
  } else if (sourcePosition === Position.Bottom && targetPosition === Position.Bottom) {
    // Source at bottom going down then up to target at bottom
    const dropDistance = 25 + Math.abs(offset) * 10
    const midY = Math.max(sourceY, targetY) + dropDistance

    const startX = sourceX + horizontalOffset
    const startY = sourceY
    const endX = targetX + horizontalOffset
    const endY = targetY + 40

    segments.push(`M ${startX} ${startY}`)

    // Go down from source
    segments.push(`L ${startX} ${midY - borderRadius}`)

    if (startX < endX) {
      segments.push(`Q ${startX} ${midY} ${startX + borderRadius} ${midY}`)
      segments.push(`L ${endX - borderRadius} ${midY}`)
      segments.push(`Q ${endX} ${midY} ${endX} ${midY - borderRadius}`)
    } else if (startX > endX) {
      segments.push(`Q ${startX} ${midY} ${startX - borderRadius} ${midY}`)
      segments.push(`L ${endX + borderRadius} ${midY}`)
      segments.push(`Q ${endX} ${midY} ${endX} ${midY - borderRadius}`)
    }

    // Go up to target
    segments.push(`L ${endX} ${endY}`)
  }

  return segments.join(' ')
}

// Custom edge with orthogonal routing, distinct colors, and proper arrow
function OrthogonalEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data }) {
  const edgeColor = data?.color || '#6B7280'
  const isRequired = data?.required !== false
  const offset = data?.offset || 0

  // Build orthogonal path
  const edgePath = buildOrthogonalPath(
    sourceX, sourceY, targetX, targetY,
    sourcePosition, targetPosition,
    offset
  )

  // Calculate arrow position and direction (pointing into target = towards parent)
  const arrowSize = 8
  const horizontalOffset = offset * 15
  const arrowX = targetX + horizontalOffset
  const arrowY = targetY + 40

  // Arrow pointing up into bottom of parent node
  const arrowPath = `M ${arrowX} ${arrowY - 2} L ${arrowX - arrowSize/2} ${arrowY + arrowSize} L ${arrowX + arrowSize/2} ${arrowY + arrowSize} Z`

  return (
    <g>
      <path
        id={id}
        d={edgePath}
        fill="none"
        stroke={edgeColor}
        strokeWidth={isRequired ? 2.5 : 2}
        strokeDasharray={isRequired ? undefined : '6,4'}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d={arrowPath}
        fill={edgeColor}
      />
    </g>
  )
}

const nodeTypes = { crd: CRDNode }
const edgeTypes = { orthogonal: OrthogonalEdge }

// Calculate handle positions distributed evenly across the node width
function calculateHandlePositions(count) {
  if (count === 0) return []
  if (count === 1) return ['50%']
  const positions = []
  for (let i = 0; i < count; i++) {
    const pct = 20 + (60 * i) / (count - 1)
    positions.push(`${pct}%`)
  }
  return positions
}

// Assign handles and calculate offsets for parallel edges
function assignHandlesAndCollect(edges, nodePositions) {
  const result = edges.map((e) => ({ ...e }))

  // Group edges by source-target pair to detect parallel edges
  const edgeGroups = {}
  result.forEach((edge) => {
    const key = [edge.source, edge.target].sort().join('-')
    if (!edgeGroups[key]) edgeGroups[key] = []
    edgeGroups[key].push(edge)
  })

  // Assign offsets to parallel edges for spacing
  Object.values(edgeGroups).forEach((group) => {
    if (group.length > 1) {
      const mid = (group.length - 1) / 2
      group.forEach((edge, i) => {
        edge.data = { ...edge.data, offset: i - mid }
      })
    }
  })

  // Collect edges by target and source
  const byTarget = {}
  const bySource = {}

  result.forEach((edge) => {
    if (!byTarget[edge.target]) byTarget[edge.target] = []
    byTarget[edge.target].push(edge)
    if (!bySource[edge.source]) bySource[edge.source] = []
    bySource[edge.source].push(edge)
  })

  const nodeHandles = {}

  // Process target handles (incoming edges)
  Object.entries(byTarget).forEach(([nodeId, targetEdges]) => {
    targetEdges.sort((a, b) => nodePositions[a.source].x - nodePositions[b.source].x)
    const positions = calculateHandlePositions(targetEdges.length)

    if (!nodeHandles[nodeId]) nodeHandles[nodeId] = { targetHandles: [], sourceHandles: [] }

    targetEdges.forEach((edge, i) => {
      const handleId = `target-${i}`
      edge.targetHandle = handleId
      nodeHandles[nodeId].targetHandles.push({ id: handleId, position: positions[i] })
    })
  })

  // Process source handles (outgoing edges)
  Object.entries(bySource).forEach(([nodeId, sourceEdges]) => {
    sourceEdges.sort((a, b) => nodePositions[a.target].x - nodePositions[b.target].x)
    const positions = calculateHandlePositions(sourceEdges.length)

    if (!nodeHandles[nodeId]) nodeHandles[nodeId] = { targetHandles: [], sourceHandles: [] }

    sourceEdges.forEach((edge, i) => {
      const handleId = `source-${i}`
      edge.sourceHandle = handleId
      nodeHandles[nodeId].sourceHandles.push({ id: handleId, position: positions[i] })
    })
  })

  return { edges: result, nodeHandles }
}

// Dagre layout with dynamic handle assignment
function getLayoutedElements(nodes, edges) {
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({ rankdir: 'TB', nodesep: 120, ranksep: 100, marginx: 50, marginy: 50 })

  const nodeWidth = 180
  const nodeHeight = 65

  nodes.forEach((node) => g.setNode(node.id, { width: nodeWidth, height: nodeHeight }))
  // Reverse edge direction for layout: parent nodes above children
  edges.forEach((edge) => g.setEdge(edge.target, edge.source))

  dagre.layout(g)

  // Build position map
  const nodePositions = {}
  nodes.forEach((node) => {
    const pos = g.node(node.id)
    nodePositions[node.id] = { x: pos.x - nodeWidth / 2, y: pos.y - nodeHeight / 2 }
  })

  // Assign handles and collect which ones each node uses
  const { edges: layoutedEdges, nodeHandles } = assignHandlesAndCollect(edges, nodePositions)

  // Build final nodes with handle info
  const layoutedNodes = nodes.map((node) => {
    const handles = nodeHandles[node.id] || { targetHandles: [], sourceHandles: [] }
    const isACL = node.id === 'ACL'
    return {
      ...node,
      position: nodePositions[node.id],
      sourcePosition: isACL ? Position.Top : Position.Bottom,
      targetPosition: Position.Bottom,
      data: {
        ...node.data,
        targetHandles: handles.targetHandles,
        sourceHandles: handles.sourceHandles,
      },
    }
  })

  return { nodes: layoutedNodes, edges: layoutedEdges }
}

export default function CRDReferenceGraph() {
  const initialNodes = useMemo(
    () =>
      crdDefinitions.map((crd) => ({
        id: crd.id,
        type: 'crd',
        data: { label: crd.label, color: crd.color, description: crd.description },
        position: { x: 0, y: 0 },
      })),
    []
  )

  const initialEdges = useMemo(() => {
    const edges = []
    crdDefinitions.forEach((crd) => {
      crd.refs.forEach((ref, idx) => {
        edges.push({
          id: `e-${crd.id}-${ref.target}-${idx}`,
          source: crd.id,
          target: ref.target,
          type: 'orthogonal',
          data: { label: ref.label, color: crd.color, required: ref.required },
        })
      })
    })
    return edges
  }, [])

  const { nodes: layoutedNodes, edges: layoutedEdges } = useMemo(
    () => getLayoutedElements(initialNodes, initialEdges),
    [initialNodes, initialEdges]
  )

  const [nodes, , onNodesChange] = useNodesState(layoutedNodes)
  const [edges, , onEdgesChange] = useEdgesState(layoutedEdges)

  return (
    <div
      style={{
        width: '100%',
        height: '650px',
        background: '#f8fafc',
        borderRadius: '12px',
        border: '1px solid #e2e8f0',
        position: 'relative',
      }}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.4}
        maxZoom={1.5}
        proOptions={{ hideAttribution: true }}
        defaultEdgeOptions={{ type: 'orthogonal' }}
      >
        <Background color="#cbd5e1" gap={24} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>

      {/* Legend */}
      <div
        style={{
          position: 'absolute',
          bottom: 16,
          left: 16,
          background: 'white',
          borderRadius: '8px',
          padding: '12px 16px',
          boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
          border: '1px solid #e2e8f0',
          zIndex: 10,
        }}
      >
        <div style={{ fontSize: '12px', fontWeight: 600, color: '#334155', marginBottom: 8 }}>Legend</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <svg width="30" height="10">
              <line x1="0" y1="5" x2="22" y2="5" stroke="#3B82F6" strokeWidth="2.5" />
              <polygon points="22,5 16,2 16,8" fill="#3B82F6" />
            </svg>
            <span style={{ fontSize: '11px', color: '#64748b' }}>Required (ownership)</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <svg width="30" height="10">
              <line x1="0" y1="5" x2="22" y2="5" stroke="#EF4444" strokeWidth="2" strokeDasharray="4,3" />
              <polygon points="22,5 16,2 16,8" fill="#EF4444" />
            </svg>
            <span style={{ fontSize: '11px', color: '#64748b' }}>Optional (reference)</span>
          </div>
        </div>
      </div>
    </div>
  )
}
