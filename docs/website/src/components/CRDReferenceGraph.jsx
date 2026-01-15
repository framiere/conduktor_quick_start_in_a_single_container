import { useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  Position,
  useNodesState,
  useEdgesState,
  Handle,
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
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

// Custom node with connection handles
function CRDNode({ data }) {
  return (
    <div
      style={{
        padding: '12px 16px',
        borderRadius: '8px',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
        border: `2px solid ${data.color}`,
        backgroundColor: data.color,
        minWidth: '160px',
        position: 'relative',
      }}
    >
      {/* Target handle (top) - where edges come IN */}
      <Handle
        type="target"
        position={Position.Top}
        style={{
          background: data.color,
          width: 10,
          height: 10,
          border: '2px solid white',
        }}
      />

      <div style={{ color: 'white', fontWeight: 'bold', fontSize: '14px', textAlign: 'center' }}>
        {data.label}
      </div>
      <div style={{ color: 'rgba(255,255,255,0.7)', fontSize: '11px', textAlign: 'center', marginTop: '4px' }}>
        {data.description}
      </div>

      {/* Source handle (bottom) - where edges go OUT */}
      <Handle
        type="source"
        position={Position.Bottom}
        style={{
          background: data.color,
          width: 10,
          height: 10,
          border: '2px solid white',
        }}
      />
    </div>
  )
}

// Custom edge with visible styling and labels
function LabeledEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data }) {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 20,
  })

  const edgeColor = data?.color || '#6B7280'
  const isRequired = data?.required !== false
  const label = data?.label || ''

  return (
    <>
      {/* The edge path */}
      <path
        id={id}
        d={edgePath}
        fill="none"
        stroke={edgeColor}
        strokeWidth={isRequired ? 3 : 2}
        strokeDasharray={isRequired ? undefined : '8,4'}
        style={{ pointerEvents: 'stroke' }}
      />
      {/* Arrow marker at the end */}
      <polygon
        points={`${targetX},${targetY - 2} ${targetX - 6},${targetY - 12} ${targetX + 6},${targetY - 12}`}
        fill={edgeColor}
      />
      {/* Edge label */}
      <EdgeLabelRenderer>
        <div
          style={{
            position: 'absolute',
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            background: 'white',
            padding: '3px 8px',
            borderRadius: '4px',
            fontSize: '10px',
            fontWeight: 600,
            color: edgeColor,
            border: `1px solid ${edgeColor}`,
            boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
            pointerEvents: 'all',
            whiteSpace: 'nowrap',
          }}
          className="nodrag nopan"
        >
          {label}
        </div>
      </EdgeLabelRenderer>
    </>
  )
}

const nodeTypes = { crd: CRDNode }
const edgeTypes = { labeled: LabeledEdge }

// Dagre layout
function getLayoutedElements(nodes, edges) {
  const g = new dagre.graphlib.Graph()
  g.setDefaultEdgeLabel(() => ({}))
  g.setGraph({ rankdir: 'TB', nodesep: 80, ranksep: 100, marginx: 40, marginy: 40 })

  const nodeWidth = 180
  const nodeHeight = 65

  nodes.forEach((node) => g.setNode(node.id, { width: nodeWidth, height: nodeHeight }))
  edges.forEach((edge) => g.setEdge(edge.source, edge.target))

  dagre.layout(g)

  const layoutedNodes = nodes.map((node) => {
    const pos = g.node(node.id)
    return {
      ...node,
      position: { x: pos.x - nodeWidth / 2, y: pos.y - nodeHeight / 2 },
      sourcePosition: Position.Bottom,
      targetPosition: Position.Top,
    }
  })

  return { nodes: layoutedNodes, edges }
}

export default function CRDReferenceGraph() {
  const initialNodes = useMemo(() =>
    crdDefinitions.map((crd) => ({
      id: crd.id,
      type: 'crd',
      data: { label: crd.label, color: crd.color, description: crd.description },
      position: { x: 0, y: 0 },
    })), []
  )

  const initialEdges = useMemo(() => {
    const edges = []
    crdDefinitions.forEach((crd) => {
      crd.refs.forEach((ref, idx) => {
        edges.push({
          id: `e-${crd.id}-${ref.target}-${idx}`,
          source: crd.id,
          target: ref.target,
          type: 'labeled',
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
    <div style={{ width: '100%', height: '650px', background: '#f8fafc', borderRadius: '12px', border: '1px solid #e2e8f0', position: 'relative' }}>
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
        defaultEdgeOptions={{ type: 'labeled' }}
      >
        <Background color="#cbd5e1" gap={24} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>

      {/* Legend */}
      <div style={{
        position: 'absolute',
        bottom: 16,
        left: 16,
        background: 'white',
        borderRadius: '8px',
        padding: '12px 16px',
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
        border: '1px solid #e2e8f0',
        zIndex: 10,
      }}>
        <div style={{ fontSize: '12px', fontWeight: 600, color: '#334155', marginBottom: 8 }}>Legend</div>
        <div style={{ display: 'flex', gap: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <svg width="30" height="4"><line x1="0" y1="2" x2="30" y2="2" stroke="#3B82F6" strokeWidth="3"/></svg>
            <span style={{ fontSize: '11px', color: '#64748b' }}>Required</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <svg width="30" height="4"><line x1="0" y1="2" x2="30" y2="2" stroke="#94a3b8" strokeWidth="2" strokeDasharray="6,3"/></svg>
            <span style={{ fontSize: '11px', color: '#64748b' }}>Optional</span>
          </div>
        </div>
      </div>
    </div>
  )
}
