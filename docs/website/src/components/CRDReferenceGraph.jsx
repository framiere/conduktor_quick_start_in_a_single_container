import { useCallback, useMemo } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MarkerType,
  Position,
  useNodesState,
  useEdgesState,
} from '@xyflow/react'
import dagre from 'dagre'
import '@xyflow/react/dist/style.css'

// CRD definitions with their colors and references
const crdDefinitions = [
  {
    id: 'ApplicationService',
    label: 'ApplicationService',
    color: '#3B82F6', // blue
    description: 'Root resource (tenant)',
    refs: [],
  },
  {
    id: 'KafkaCluster',
    label: 'KafkaCluster',
    color: '#8B5CF6', // purple
    description: 'Virtual Kafka cluster',
    refs: [{ target: 'ApplicationService', label: 'applicationServiceRef', required: true }],
  },
  {
    id: 'ServiceAccount',
    label: 'ServiceAccount',
    color: '#10B981', // green
    description: 'Client identity',
    refs: [
      { target: 'KafkaCluster', label: 'clusterRef', required: true },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
  {
    id: 'Topic',
    label: 'Topic',
    color: '#F59E0B', // orange
    description: 'Kafka topic',
    refs: [
      { target: 'ServiceAccount', label: 'serviceRef', required: true },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
  {
    id: 'ConsumerGroup',
    label: 'ConsumerGroup',
    color: '#6B7280', // gray
    description: 'Consumer group',
    refs: [
      { target: 'ServiceAccount', label: 'serviceRef', required: true },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
  {
    id: 'ACL',
    label: 'ACL',
    color: '#EF4444', // red
    description: 'Access control',
    refs: [
      { target: 'ServiceAccount', label: 'serviceRef', required: true },
      { target: 'Topic', label: 'topicRef', required: false },
      { target: 'ConsumerGroup', label: 'consumerGroupRef', required: false },
      { target: 'ApplicationService', label: 'applicationServiceRef', required: true },
    ],
  },
]

// Custom node component
function CRDNode({ data }) {
  return (
    <div
      className="px-4 py-3 rounded-lg shadow-lg border-2 min-w-[160px]"
      style={{
        backgroundColor: data.color,
        borderColor: data.color,
      }}
    >
      <div className="text-white font-bold text-sm text-center">{data.label}</div>
      <div className="text-white/70 text-xs text-center mt-1">{data.description}</div>
    </div>
  )
}

const nodeTypes = {
  crd: CRDNode,
}

// Use dagre for automatic hierarchical layout
function getLayoutedElements(nodes, edges) {
  const dagreGraph = new dagre.graphlib.Graph()
  dagreGraph.setDefaultEdgeLabel(() => ({}))

  const nodeWidth = 180
  const nodeHeight = 70

  dagreGraph.setGraph({
    rankdir: 'TB', // Top to bottom
    nodesep: 80,
    ranksep: 100,
    marginx: 50,
    marginy: 50,
  })

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: nodeWidth, height: nodeHeight })
  })

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target)
  })

  dagre.layout(dagreGraph)

  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id)
    return {
      ...node,
      position: {
        x: nodeWithPosition.x - nodeWidth / 2,
        y: nodeWithPosition.y - nodeHeight / 2,
      },
      sourcePosition: Position.Bottom,
      targetPosition: Position.Top,
    }
  })

  return { nodes: layoutedNodes, edges }
}

export default function CRDReferenceGraph() {
  // Build initial nodes from definitions
  const initialNodes = useMemo(() =>
    crdDefinitions.map((crd) => ({
      id: crd.id,
      type: 'crd',
      data: {
        label: crd.label,
        color: crd.color,
        description: crd.description,
      },
      position: { x: 0, y: 0 },
    })), []
  )

  // Build edges from reference definitions
  const initialEdges = useMemo(() => {
    const edges = []
    crdDefinitions.forEach((crd) => {
      crd.refs.forEach((ref, index) => {
        edges.push({
          id: `${crd.id}-${ref.target}-${index}`,
          source: crd.id,
          target: ref.target,
          label: ref.label,
          type: 'smoothstep',
          animated: !ref.required,
          style: {
            stroke: ref.required ? crd.color : '#9CA3AF',
            strokeWidth: ref.required ? 2 : 1.5,
            strokeDasharray: ref.required ? undefined : '5,5',
          },
          labelStyle: {
            fill: ref.required ? crd.color : '#6B7280',
            fontSize: 10,
            fontWeight: 500,
          },
          labelBgStyle: {
            fill: 'white',
            fillOpacity: 0.9,
          },
          labelBgPadding: [4, 2],
          labelBgBorderRadius: 4,
          markerEnd: {
            type: MarkerType.ArrowClosed,
            color: ref.required ? crd.color : '#9CA3AF',
            width: 20,
            height: 20,
          },
        })
      })
    })
    return edges
  }, [])

  // Apply dagre layout
  const { nodes: layoutedNodes, edges: layoutedEdges } = useMemo(
    () => getLayoutedElements(initialNodes, initialEdges),
    [initialNodes, initialEdges]
  )

  const [nodes, setNodes, onNodesChange] = useNodesState(layoutedNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(layoutedEdges)

  return (
    <div className="w-full h-[600px] bg-gray-50 dark:bg-gray-800 rounded-xl overflow-hidden border border-gray-200 dark:border-gray-700">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.5}
        maxZoom={1.5}
        defaultEdgeOptions={{
          type: 'smoothstep',
        }}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="#d1d5db" gap={20} size={1} />
        <Controls
          className="bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg"
          showInteractive={false}
        />
      </ReactFlow>

      {/* Legend */}
      <div className="absolute bottom-4 left-4 bg-white dark:bg-gray-900 rounded-lg px-4 py-3 shadow-lg border border-gray-200 dark:border-gray-700">
        <div className="text-xs font-semibold text-gray-700 dark:text-gray-300 mb-2">Legend</div>
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-2">
            <div className="w-8 h-0.5 bg-blue-500"></div>
            <span className="text-xs text-gray-600 dark:text-gray-400">Required ref</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-8 h-0.5 bg-gray-400 border-dashed" style={{ borderTop: '2px dashed #9CA3AF' }}></div>
            <span className="text-xs text-gray-600 dark:text-gray-400">Optional ref</span>
          </div>
        </div>
      </div>
    </div>
  )
}
