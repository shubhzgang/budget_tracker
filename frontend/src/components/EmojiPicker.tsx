import React, { useState, useRef, useEffect } from 'react';

export const EMOJI_SECTIONS = [
  {
    label: 'Smileys & Emotion',
    emoji: '😀',
    emojis: [
      '😀','😃','😄','😁','😆','😅','🤣','😂','🙂','🙃','🫠','😉','😊','😇','🥰','😍','🤩','😘','😗','😚','😙','🥲','😋','😛','😜','🤪','😝','🤑','🤗','🫡','🤭','🫢','🫣','🤫','🤔','🫤','😐','😑','😶','🫥','😏','😒','🙄','😬','🤥','🫨','😌','😔','😪','🤤','😴','😷','🤒','🤕','🤢','🤮','🤧','🥵','🥶','🥴','😵','🤯','🤠','🥳','🥸','😎','🤓','🧐','😕','🫢','😟','🙁','☹️','😮','😯','😲','😳','🥺','🥹','😦','😧','😨','😰','😥','😢','😭','😱','😖','😣','😞','😓','😩','😫','🥱','😤','😡','😠','🤬','😈','👿','💀','☠️','💩','🤡','👹','👺','👻','👽','👾','🤖',
    ],
  },
  {
    label: 'People & Body',
    emoji: '👋',
    emojis: [
      '👋','🤚','🖐️','✋','🖖','🫱','🫲','🫳','🫴','🫷','🫸','👌','🤌','🤏','✌️','🤞','🫰','🤟','🤘','🤙','👈','👉','👆','🖕','👇','☝️','🫵','👍','👎','✊','👊','🤛','🤜','👏','🙌','🫶','👐','🤲','🤝','🙏','✍️','💅','🤳','💪','🦾','🦿','🦵','🦶','👂','🦻','👃','🫀','🫁','🧠','🦷','🦴','👀','👁️','👅','👄','🫦','👶','🧒','👦','👧','🧑','👱','👨','🧔','👩','🧓','👴','👵','🙍','🙎','🙅','🙆','💁','🙋','🧏','🙇','🤦','🤷','👮','🕵️','💂','🥷','👷','🫅','🤴','👸','👳','👲','🧕','🤵','👰','🤰','🫃','🫄','🤱','👼','🎅','🤶','🧑‍🎄','🦸','🦹','🧙','🧝','🧛','🧟','🧌','🧞','🧜','🧚','🧑‍🦰','🧑‍🦱','🧑‍🦳','🧑‍🦲','👫','👬','👭','💏','💑','👪',
    ],
  },
  {
    label: 'Animals & Nature',
    emoji: '🐶',
    emojis: [
      '🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐻‍❄️','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🙈','🙉','🙊','🐒','🐔','🐧','🐦','🐤','🦆','🦅','🦉','🦇','🐺','🐗','🐴','🦄','🐝','🪱','🐛','🦋','🐌','🐞','🐜','🪲','🦟','🦗','🪳','🕷️','🦂','🐢','🐍','🦎','🦖','🦕','🐙','🦑','🦐','🦞','🦀','🐡','🐠','🐟','🐬','🐳','🐋','🦈','🦭','🐊','🐅','🐆','🦓','🫏','🦍','🦧','🦣','🐘','🦛','🦏','🐪','🐫','🦒','🦘','🦬','🐃','🐂','🐄','🫎','🐎','🐖','🐏','🐑','🦙','🐐','🦌','🐕','🐩','🦮','🐕‍🦺','🐈','🐈‍⬛','🪶','🐓','🦃','🦤','🦚','🦜','🦢','🕊️','🐇','🦝','🦨','🦡','🦫','🦦','🦥','🐁','🐀','🐿️','🦔','🐾','🐉','🐲','🌵','🎄','🌲','🌳','🌴','🪵','🌱','🌿','☘️','🍀','🎍','🪴','🎋','🍃','🍂','🍁','🪺','🪹','🍄','🐚','🪸','🪨','🌾','💐','🌷','🌹','🥀','🪷','🌺','🌸','🌼','🌻','🌞','🌝','🌛','🌜','🌚','🌕','🌖','🌗','🌘','🌑','🌒','🌓','🌔','🌙','🌟','⭐','🌠','🌌','☁️','⛅','🌤️','🌥️','🌦️','🌧️','⛈️','🌩️','🌨️','❄️','☃️','⛄','🌬️','💨','💧','💦','🫧','☔','☂️','🌊','🌫️',
    ],
  },
  {
    label: 'Food & Drink',
    emoji: '🍎',
    emojis: [
      '🍏','🍎','🍐','🍊','🍋','🍌','🍉','🍇','🍓','🫐','🍈','🍒','🍑','🥭','🍍','🥥','🥝','🍅','🍆','🥑','🥦','🫛','🥬','🥒','🌶️','🫑','🧄','🧅','🥔','🍠','🫚','🥐','🥯','🍞','🥖','🥨','🧀','🥚','🍳','🧈','🥞','🧇','🥓','🥩','🍗','🍖','🦴','🌭','🍔','🍟','🍕','🫓','🥪','🥙','🧆','🌮','🌯','🫔','🥗','🥘','🫕','🥫','🫙','🍝','🍜','🍲','🍛','🍣','🍱','🥟','🦪','🍤','🍙','🍚','🍘','🍥','🥮','🍢','🧁','🍡','🍧','🍨','🍦','🥧','🍰','🎂','🍮','🍭','🍬','🍫','🍿','🍩','🍪','🌰','🥜','🍯','🧃','🥤','🧋','🍵','☕','🫖','🍺','🍻','🥂','🍷','🫗','🥃','🍸','🍹','🧉','🍾','🧊','🥄','🍴','🍽️','🥢','🫙',
    ],
  },
  {
    label: 'Travel & Places',
    emoji: '🚗',
    emojis: [
      '🚗','🚕','🚙','🚌','🚎','🏎️','🚓','🚑','🚒','🚐','🛻','🚚','🚛','🚜','🏍️','🛵','🛺','🚲','🛴','🛹','🛼','🚏','🛣️','🛤️','⛽','🚧','⚓','🛟','⛵','🚤','🛥️','🛳️','⛴️','🚢','✈️','🛩️','🛫','🛬','🪂','💺','🚁','🚟','🚠','🚡','🛰️','🚀','🛸','🎡','🎢','🎠','🏗️','🌁','🗼','🏢','🏬','🏣','🏤','🏥','🏦','🏨','🏩','💒','🏛️','⛪','🕌','🕍','🛕','🕋','⛩️','🗾','🎑','🏞️','🌅','🌄','🌠','🎇','🎆','🌃','🏙️','🌉','🌌','🌁','🗺️','🧭','🏔️','⛰️','🌋','🗻','🏕️','🏖️','🏜️','🏝️','🏟️','🏛️','🏗️','🧱','🪨','🪵','🛖','🏘️','🏚️','🏠','🏡','🏢','🏣','🏤','🏥','🏦','🏨','🏩','🏪','🏫','🏭','🏯','🏰','💎','🗽','🗿','🗺️',
    ],
  },
  {
    label: 'Activities',
    emoji: '⚽',
    emojis: [
      '⚽','🏀','🏈','⚾','🥎','🎾','🏐','🏉','🥏','🎱','🪀','🏓','🏸','🏒','🥍','🏑','🥊','🥋','🎽','🛹','🛼','🛷','⛸️','🥌','🎿','⛷️','🏂','🪂','🏋️','🤼','🤸','⛹️','🤺','🏇','🧘','🏄','🏊','🤽','🚣','🧗','🚵','🚴','🏆','🥇','🥈','🥉','🏅','🎖️','🏵️','🎗️','🎫','🎟️','🎪','🤹','🎭','🎨','🎬','🎤','🎧','🎼','🎵','🎶','🎷','🪗','🎸','🎹','🎺','🎻','🪕','🥁','🪘','🎯','🎳','🎮','🎰','🧩','🪄','🎲','♟️','🎭','🖼️','🎨','🧵','🪡','🧶','🪢',
    ],
  },
  {
    label: 'Objects',
    emoji: '💡',
    emojis: [
      '💡','🔦','🕯️','🪔','🧱','💰','💴','💵','💶','💷','💸','💳','🪙','💹','📈','📉','📊','💼','🧳','📁','📂','🗂️','📋','📌','📍','📎','🖇️','📏','📐','✂️','🗃️','🗄️','🗑️','🔒','🔓','🔏','🔐','🔑','🗝️','🔨','🪓','⛏️','⚒️','🛠️','🗡️','⚔️','🔫','🛡️','🪚','🔧','🪛','🔩','⚙️','🗜️','🔗','⛓️','🪝','🧰','🧲','🪜','⚗️','🔭','🔬','🩺','🩻','💊','🩹','🩼','🩺','🪒','🧴','🧷','🧹','🧺','🧻','🪣','🧼','🫧','🪥','🧽','🧯','🛒','🚪','🪞','🪟','🛏️','🛋️','🚽','🪠','🚿','🛁','🪤','🧸','🪆','🖼️','🪞','🛍️','📱','💻','🖥️','🖨️','⌨️','🖱️','📷','📸','📹','🎥','📽️','📞','☎️','📟','📠','📺','📻','🧭','⏱️','⏲️','⏰','🕰️','⌚','📡','🔋','🪫','🔌','💡','🔦','🕯️','🔭','📡',
    ],
  },
  {
    label: 'Symbols',
    emoji: '❤️',
    emojis: [
      '❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❤️‍🔥','❤️‍🩹','❣️','💕','💞','💓','💗','💖','💘','💝','💟','☮️','✝️','☪️','🕉️','✡️','🔯','🕎','☯️','☦️','🛐','⛎','♈','♉','♊','♋','♌','♍','♎','♏','♐','♑','♒','♓','🆔','⚛️','🉑','☢️','☣️','📴','📳','🈶','🈚','🈸','🈺','🈷️','✴️','🆚','💮','🉐','㊙️','㊗️','🈴','🈵','🈹','🈲','🅰️','🅱️','🆎','🆑','🅾️','🆘','❌','⭕','🛑','⛔','📛','🚫','💯','💢','♨️','🚷','🚯','🚳','🚱','🔞','📵','🔕','🔇','🔈','🔉','🔊','📢','📣','🔔','🔕','🎵','🎶','⚠️','🚸','♻️','✅','❎','🆗','🆙','🆒','🆕','🆓','🔟','🔠','🔡','🔢','🔣','🔤','🅰️','🅱️','🆎','🆑','🅾️','🆘','🚾','🩺','⚕️','♾️','🔱','📛','🔰','⭕','✔️','❌','❓','❔','❕','❗','〰️','💱','💲','➕','➖','➗','✖️','🟰','♾️','‼️','⁉️','🔅','🔆','〽️','⚜️','🔷','🔶','🔹','🔸','🔺','🔻','💠','🔘','🔳','🔲',
    ],
  },
  {
    label: 'Flags',
    emoji: '🏁',
    emojis: [
      '🏁','🚩','🎌','🏴','🏳️','🏳️‍🌈','🏳️‍⚧️','🏴‍☠️','🇦🇫','🇦🇱','🇩🇿','🇦🇩','🇦🇴','🇦🇬','🇦🇷','🇦🇲','🇦🇺','🇦🇹','🇦🇿','🇧🇸','🇧🇭','🇧🇩','🇧🇧','🇧🇾','🇧🇪','🇧🇿','🇧🇯','🇧🇹','🇧🇴','🇧🇦','🇧🇼','🇧🇷','🇧🇳','🇧🇬','🇧🇫','🇧🇮','🇨🇻','🇨🇦','🇨🇫','🇹🇩','🇨🇱','🇨🇳','🇨🇴','🇰🇲','🇨🇬','🇨🇩','🇨🇷','🇨🇮','🇭🇷','🇨🇺','🇨🇾','🇨🇿','🇩🇰','🇩🇯','🇩🇲','🇩🇴','🇪🇨','🇪🇬','🇸🇻','🇬🇶','🇪🇷','🇪🇪','🇸🇿','🇪🇹','🇫🇯','🇫🇮','🇫🇷','🇬🇦','🇬🇲','🇬🇪','🇩🇪','🇬🇭','🇬🇷','🇬🇩','🇬🇹','🇬🇳','🇬🇼','🇬🇾','🇭🇹','🇭🇳','🇭🇺','🇮🇸','🇮🇳','🇮🇩','🇮🇷','🇮🇶','🇮🇪','🇮🇱','🇮🇹','🇯🇲','🇯🇵','🇯🇴','🇰🇿','🇰🇪','🇰🇮','🇰🇼','🇰🇬','🇱🇦','🇱🇻','🇱🇧','🇱🇸','🇱🇷','🇱🇾','🇱🇮','🇱🇹','🇱🇺','🇲🇬','🇲🇼','🇲🇾','🇲🇻','🇲🇱','🇲🇹','🇲🇭','🇲🇷','🇲🇺','🇲🇽','🇫🇲','🇲🇩','🇲🇨','🇲🇳','🇲🇪','🇲🇦','🇲🇿','🇲🇲','🇳🇦','🇳🇷','🇳🇵','🇳🇱','🇳🇿','🇳🇮','🇳🇪','🇳🇬','🇰🇵','🇲🇰','🇳🇴','🇴🇲','🇵🇰','🇵🇼','🇵🇦','🇵🇬','🇵🇾','🇵🇪','🇵🇭','🇵🇱','🇵🇹','🇶🇦','🇷🇴','🇷🇺','🇷🇼','🇰🇳','🇱🇨','🇻🇨','🇼🇸','🇸🇲','🇸🇹','🇸🇦','🇸🇳','🇷🇸','🇸🇱','🇸🇬','🇸🇰','🇸🇮','🇸🇧','🇸🇴','🇿🇦','🇰🇷','🇸🇸','🇪🇸','🇱🇰','🇸🇩','🇸🇷','🇸🇪','🇨🇭','🇸🇾','🇹🇼','🇹🇯','🇹🇿','🇹🇭','🇹🇱','🇹🇬','🇹🇴','🇹🇹','🇹🇳','🇹🇷','🇹🇲','🇹🇻','🇺🇬','🇺🇦','🇦🇪','🇬🇧','🇺🇸','🇺🇾','🇺🇿','🇻🇺','🇻🇪','🇻🇳','🇾🇪','🇿🇲','🇿🇼',
    ],
  },
];

interface EmojiPickerProps {
  value: string;
  onChange: (emoji: string) => void;
}

export const EmojiPicker: React.FC<EmojiPickerProps> = ({ value, onChange }) => {
  const [open, setOpen] = useState(false);
  const [activeSection, setActiveSection] = useState(0);
  const [search, setSearch] = useState('');
  const popoverRef = useRef<HTMLDivElement>(null);
  const sectionRefs = useRef<(HTMLDivElement | null)[]>([]);
  const gridRef = useRef<HTMLDivElement>(null);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setOpen(false);
        setSearch('');
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const handleSelect = (emoji: string) => {
    onChange(emoji);
    setOpen(false);
    setSearch('');
  };

  const scrollToSection = (index: number) => {
    setActiveSection(index);
    sectionRefs.current[index]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const filteredSections = search.trim()
    ? [{ label: 'Search Results', emoji: '🔍', emojis: EMOJI_SECTIONS.flatMap(s => s.emojis).filter(e => e.includes(search.trim())) }]
    : EMOJI_SECTIONS;

  return (
    <div className="relative" ref={popoverRef}>
      {/* Trigger button */}
      <button
        type="button"
        onClick={() => setOpen(v => !v)}
        className="flex items-center gap-2 w-full border border-input bg-background text-foreground p-2 rounded-md focus:ring-2 focus:ring-ring outline-none hover:bg-secondary/50 transition-colors"
      >
        <span className="text-xl leading-none">{value}</span>
        <span className="text-sm text-muted-foreground flex-1 text-left">Pick emoji</span>
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" className="w-4 h-4 text-muted-foreground">
          <path fillRule="evenodd" d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z" clipRule="evenodd" />
        </svg>
      </button>

      {/* Popover */}
      {open && (
        <div className="absolute z-50 mt-1 left-0 w-80 bg-card border border-border rounded-xl shadow-2xl flex flex-col overflow-hidden"
          style={{ maxHeight: '380px' }}>

          {/* Search bar */}
          <div className="p-2 border-b border-border">
            <input
              autoFocus
              type="text"
              placeholder="Search emoji…"
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full bg-background text-foreground text-sm border border-input rounded-md px-3 py-1.5 outline-none focus:ring-2 focus:ring-ring"
            />
          </div>

          {/* Section tab bar */}
          {!search && (
            <div className="flex overflow-x-auto border-b border-border bg-card scrollbar-hide">
              {EMOJI_SECTIONS.map((section, i) => (
                <button
                  key={section.label}
                  type="button"
                  title={section.label}
                  onClick={() => scrollToSection(i)}
                  className={`flex-shrink-0 px-2 py-1.5 text-lg transition-colors hover:bg-secondary/60 ${activeSection === i ? 'border-b-2 border-primary bg-secondary/40' : ''}`}
                >
                  {section.emoji}
                </button>
              ))}
            </div>
          )}

          {/* Emoji grid */}
          <div ref={gridRef} className="overflow-y-auto flex-1 p-2 space-y-3">
            {filteredSections.map((section, si) => (
              <div key={section.label} ref={el => { sectionRefs.current[si] = el; }}>
                <p className="text-[10px] font-semibold uppercase tracking-widest text-muted-foreground mb-1 px-1">
                  {section.label}
                </p>
                <div className="grid grid-cols-8 gap-0.5">
                  {section.emojis.map(emoji => (
                    <button
                      key={emoji}
                      type="button"
                      onClick={() => handleSelect(emoji)}
                      title={emoji}
                      className={`text-xl p-1 rounded-md hover:bg-secondary transition-colors leading-none ${value === emoji ? 'bg-primary/20 ring-1 ring-primary' : ''}`}
                    >
                      {emoji}
                    </button>
                  ))}
                </div>
              </div>
            ))}
            {filteredSections[0]?.emojis.length === 0 && (
              <p className="text-sm text-muted-foreground text-center py-6">No emoji found</p>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
