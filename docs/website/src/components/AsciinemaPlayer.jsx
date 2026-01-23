import { useEffect, useRef } from 'react'
import 'asciinema-player/dist/bundle/asciinema-player.css'

export default function AsciinemaPlayer({ src, title, autoPlay = false, loop = false, speed = 1, theme = 'asciinema', className = '' }) {
  const containerRef = useRef(null)
  const playerRef = useRef(null)

  useEffect(() => {
    const loadPlayer = async () => {
      if (!containerRef.current) return

      const AsciinemaPlayerLibrary = await import('asciinema-player')

      if (playerRef.current) {
        playerRef.current = null
        containerRef.current.innerHTML = ''
      }

      playerRef.current = AsciinemaPlayerLibrary.create(src, containerRef.current, {
        autoPlay,
        loop,
        speed,
        theme,
        fit: 'width',
        terminalFontSize: 'small',
        controls: true,
        idleTimeLimit: 2,
        poster: 'npt:0:0'
      })
    }

    loadPlayer()

    return () => {
      if (playerRef.current) {
        playerRef.current = null
      }
    }
  }, [src, autoPlay, loop, speed, theme])

  return (
    <div className={`rounded-xl overflow-hidden border border-gray-200 dark:border-gray-800 bg-gray-900 ${className}`}>
      {title && (
        <div className="px-4 py-2 bg-gray-800 border-b border-gray-700 text-sm text-gray-300 font-mono">
          {title}
        </div>
      )}
      <div ref={containerRef} className="asciinema-player-container" />
    </div>
  )
}
