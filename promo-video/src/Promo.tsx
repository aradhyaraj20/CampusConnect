import React from 'react';
import {
  AbsoluteFill,
  Easing,
  Sequence,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
  OffthreadVideo,
  Audio,
} from 'remotion';

const FPS = 30;
const NATIVE_W = 1080;
const NATIVE_H = 2424;
const PHONE_W = 700;
const SCALE = PHONE_W / NATIVE_W;
const PHONE_H = Math.round(NATIVE_H * SCALE);
const PHONE_TOP = 330;

const FONT = 'Roboto, "Segoe UI", Arial, sans-serif';
const APP_BG = '#CCE7FA';

type Patch = {x: number; y: number; w: number; h: number; color?: string; blur?: number};

type SceneCfg = {
  id: string;
  frames: number;
  src: string;
  start: number; // seconds into the source clip
  rate: number; // playback speed
  title: string;
  sub: string;
  patches?: Patch[];
  captions: {from: number; to: number; text: string}[];
};

// Patches are in the recording's native pixel space (1080x2424).
const KEYBOARD_TOOLBAR: Patch = {x: 8, y: 870, w: 270, h: 780, color: APP_BG};
const PROFILE_EMAIL: Patch = {x: 320, y: 535, w: 400, h: 75, blur: 18};

export const SCENES: SceneCfg[] = [
  {
    id: 'hook',
    captions: [{from: 20, to: 75, text: "Everything happening on campus"}, {from: 80, to: 138, text: "Finally in one place"}],
    frames: 150,
    src: 'clip0_splash.mp4',
    start: 5.7,
    rate: 1,
    title: 'Campus life.\nOne app.',
    sub: 'Events · Groups · Tickets · Chat',
  },
  {
    id: 'discover',
    captions: [{from: 25, to: 100, text: "Browse upcoming events at a glance"}, {from: 110, to: 195, text: "Personalised picks, right on top"}],
    frames: 210,
    src: 'clip1_home.mp4',
    start: 3.5,
    rate: 1.15,
    title: "Discover what's on",
    sub: 'Upcoming events and picks for you',
  },
  {
    id: 'rsvp',
    captions: [{from: 25, to: 115, text: "Tap once to grab your spot"}, {from: 125, to: 225, text: "Live seat count updates instantly"}],
    frames: 240,
    src: 'clip2_rsvp.mp4',
    start: 5,
    rate: 1.4,
    title: 'RSVP in one tap',
    sub: 'See who is going and how many seats are left',
  },
  {
    id: 'chat',
    captions: [{from: 25, to: 115, text: "Joined? You're in the event chat"}, {from: 125, to: 225, text: "Meet people before the day"}],
    frames: 240,
    src: 'clip7_chat.mp4',
    start: 10,
    rate: 1.7,
    title: 'Chat with everyone going',
    sub: 'Event chat unlocks when you join',
    patches: [KEYBOARD_TOOLBAR],
  },
  {
    id: 'qr',
    captions: [{from: 15, to: 95, text: "Your ticket lives in your profile"}, {from: 105, to: 185, text: "Show the QR at the door"}],
    frames: 195,
    src: 'clip5_qr.mp4',
    start: 3.5,
    rate: 1,
    title: 'Your QR ticket',
    sub: 'Organizers scan it to confirm attendance',
    patches: [PROFILE_EMAIL],
  },
  {
    id: 'ai',
    captions: [{from: 25, to: 115, text: "Just ask, in plain language"}, {from: 125, to: 225, text: "Get event suggestions instantly"}],
    frames: 240,
    src: 'clip3_ai.mp4',
    start: 5,
    rate: 1.3,
    title: 'Ask the campus assistant',
    sub: 'Find events in plain language',
  },
  {
    id: 'groups',
    captions: [{from: 25, to: 105, text: "Find study groups for your courses"}, {from: 115, to: 195, text: "Earn karma, climb the leaderboard"}],
    frames: 210,
    src: 'clip4_groups.mp4',
    start: 5,
    rate: 1.3,
    title: 'Join study groups',
    sub: 'Climb the course karma leaderboard',
  },
  {
    id: 'events',
    captions: [{from: 25, to: 100, text: "All your joined events together"}, {from: 110, to: 195, text: "Never miss a thing"}],
    frames: 210,
    src: 'clip6_events.mp4',
    start: 6.5,
    rate: 1.2,
    title: 'Your calendar, sorted',
    sub: 'Every event you join, in one place',
  },
];

const VO_DELAY = 12;
const CTA_FRAMES = 240;
export const TOTAL_FRAMES = SCENES.reduce((a, s) => a + s.frames, 0) + CTA_FRAMES;

const Background: React.FC = () => {
  const frame = useCurrentFrame();
  const t = frame / FPS;
  return (
    <AbsoluteFill
      style={{
        background: `linear-gradient(${150 + Math.sin(t / 3) * 12}deg, #2A3A9E 0%, #4A32AE 50%, #6A35B8 100%)`,
      }}
    >
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          style={{
            position: 'absolute',
            width: 700,
            height: 700,
            borderRadius: 700,
            background: i === 1 ? 'rgba(255,193,7,0.10)' : 'rgba(255,255,255,0.07)',
            left: 100 + i * 330 + Math.sin(t / 2 + i * 2) * 60,
            top: 200 + i * 520 + Math.cos(t / 2.5 + i) * 70,
            filter: 'blur(40px)',
          }}
        />
      ))}
    </AbsoluteFill>
  );
};

const ProgressBar: React.FC = () => {
  const frame = useCurrentFrame();
  return (
    <div
      style={{
        position: 'absolute',
        left: 60,
        right: 60,
        top: 36,
        height: 6,
        borderRadius: 3,
        background: 'rgba(255,255,255,0.18)',
      }}
    >
      <div
        style={{
          width: `${(frame / TOTAL_FRAMES) * 100}%`,
          height: '100%',
          borderRadius: 3,
          background: '#FFC107',
        }}
      />
    </div>
  );
};

const Caption: React.FC<{title: string; sub: string; frames: number}> = ({title, sub, frames}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const inP = spring({frame, fps, config: {damping: 200}, durationInFrames: 18});
  const outP = interpolate(frame, [frames - 10, frames], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const subP = spring({frame: frame - 8, fps, config: {damping: 200}, durationInFrames: 18});
  return (
    <div
      style={{
        position: 'absolute',
        top: 90,
        left: 60,
        right: 60,
        height: 230,
        opacity: outP,
        fontFamily: FONT,
        textAlign: 'center',
        color: 'white',
      }}
    >
      <div
        style={{
          fontSize: title.includes('\n') ? 96 : 76,
          fontWeight: 800,
          lineHeight: 1.05,
          whiteSpace: 'pre-line',
          letterSpacing: -1,
          transform: `translateY(${(1 - inP) * 40}px)`,
          opacity: inP,
          textShadow: '0 4px 24px rgba(0,0,0,0.25)',
        }}
      >
        {title}
      </div>
      <div
        style={{
          marginTop: 18,
          fontSize: 38,
          fontWeight: 500,
          color: 'rgba(255,255,255,0.88)',
          transform: `translateY(${(1 - subP) * 30}px)`,
          opacity: subP,
        }}
      >
        {sub}
      </div>
    </div>
  );
};

const Phone: React.FC<{cfg: SceneCfg}> = ({cfg}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const inP = spring({frame, fps, config: {damping: 18, stiffness: 110}, durationInFrames: 28});
  const out = interpolate(frame, [cfg.frames - 10, cfg.frames], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: Easing.in(Easing.quad),
  });
  const float = Math.sin(frame / 22) * 6;
  return (
    <div
      style={{
        position: 'absolute',
        left: (1080 - PHONE_W) / 2,
        top: PHONE_TOP,
        width: PHONE_W,
        height: PHONE_H,
        transform: `translateY(${(1 - inP) * 220 + float}px) scale(${0.92 + inP * 0.08})`,
        opacity: Math.min(1, inP * 1.4) * out,
        borderRadius: 78,
        boxShadow: '0 50px 120px rgba(10,5,50,0.55), 0 0 0 10px #0B0B14',
        overflow: 'hidden',
        background: '#000',
      }}
    >
      <div
        style={{
          width: NATIVE_W,
          height: NATIVE_H,
          transform: `scale(${SCALE})`,
          transformOrigin: 'top left',
          position: 'relative',
        }}
      >
        <OffthreadVideo
          src={staticFile(cfg.src)}
          startFrom={Math.round(cfg.start * FPS)}
          playbackRate={cfg.rate}
          muted
          style={{width: NATIVE_W, height: NATIVE_H}}
        />
        {(cfg.patches ?? []).map((p, i) => (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: p.x,
              top: p.y,
              width: p.w,
              height: p.h,
              background: p.blur ? 'rgba(204,231,250,0.35)' : p.color,
              backdropFilter: p.blur ? `blur(${p.blur}px)` : undefined,
              borderRadius: 16,
            }}
          />
        ))}
      </div>
    </div>
  );
};

const SubtitleLine: React.FC<{cfg: SceneCfg}> = ({cfg}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const cap = cfg.captions.find((c) => frame >= c.from && frame < c.to);
  if (!cap) return null;
  const inP = spring({frame: frame - cap.from, fps, config: {damping: 200}, durationInFrames: 10});
  const outP = interpolate(frame, [cap.to - 6, cap.to], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return (
    <div
      style={{
        position: 'absolute',
        left: 80,
        right: 80,
        top: PHONE_TOP + PHONE_H - 330,
        display: 'flex',
        justifyContent: 'center',
        opacity: inP * outP,
        transform: `translateY(${(1 - inP) * 20}px)`,
        fontFamily: FONT,
      }}
    >
      <div
        style={{
          padding: '16px 32px',
          maxWidth: 640,
          borderRadius: 60,
          background: 'rgba(11,11,20,0.82)',
          color: 'white',
          fontSize: 36,
          fontWeight: 600,
          textAlign: 'center',
          boxShadow: '0 12px 40px rgba(0,0,0,0.35)',
        }}
      >
        {cap.text}
      </div>
    </div>
  );
};

const SceneView: React.FC<{cfg: SceneCfg}> = ({cfg}) => (
  <AbsoluteFill>
    <Caption title={cfg.title} sub={cfg.sub} frames={cfg.frames} />
    <Phone cfg={cfg} />
    <SubtitleLine cfg={cfg} />
    <Sequence from={VO_DELAY}>
      <Audio src={staticFile(`vo/${cfg.id}.wav`)} />
    </Sequence>
  </AbsoluteFill>
);

const Cta: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const a = spring({frame, fps, config: {damping: 200}, durationInFrames: 24});
  const b = spring({frame: frame - 14, fps, config: {damping: 200}, durationInFrames: 24});
  const c = spring({frame: frame - 28, fps, config: {damping: 14, stiffness: 120}});
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', fontFamily: FONT, color: 'white'}}>
      <Sequence from={VO_DELAY}>
        <Audio src={staticFile('vo/cta.wav')} />
      </Sequence>
      <div style={{opacity: a, transform: `scale(${0.8 + a * 0.2})`}}>
        <svg width="190" height="190" viewBox="0 0 100 100">
          <polygon points="50,6 50,66 14,80" fill="#FFC107" />
          <polygon points="50,6 86,80 50,66" fill="#FFFFFF" />
        </svg>
      </div>
      <div style={{fontSize: 118, fontWeight: 800, letterSpacing: -2, marginTop: 24, opacity: a}}>
        CampusConnect
      </div>
      <div
        style={{
          fontSize: 50,
          fontWeight: 500,
          marginTop: 12,
          opacity: b,
          transform: `translateY(${(1 - b) * 30}px)`,
          color: 'rgba(255,255,255,0.9)',
        }}
      >
        Connect. Study. Grow.
      </div>
      <div
        style={{
          marginTop: 70,
          padding: '26px 64px',
          borderRadius: 100,
          background: '#FFC107',
          color: '#2A1B6B',
          fontSize: 44,
          fontWeight: 800,
          opacity: c,
          transform: `scale(${0.7 + c * 0.3})`,
          boxShadow: '0 18px 50px rgba(0,0,0,0.3)',
        }}
      >
        Android · Works offline
      </div>
    </AbsoluteFill>
  );
};

export const Promo: React.FC = () => {
  let at = 0;
  return (
    <AbsoluteFill style={{background: '#2A3A9E'}}>
      <Background />
      {SCENES.map((s) => {
        const from = at;
        at += s.frames;
        return (
          <Sequence key={s.id} from={from} durationInFrames={s.frames} name={s.id}>
            <SceneView cfg={s} />
          </Sequence>
        );
      })}
      <Sequence from={at} durationInFrames={CTA_FRAMES} name="cta">
        <Cta />
      </Sequence>
      <ProgressBar />
    </AbsoluteFill>
  );
};
