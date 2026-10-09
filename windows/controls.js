'use strict';
// One dropdown implementation for mouse, touch and a directional HID remote.
// Keep native option/value semantics; never depend on a Windows popup theme.
class SamaSelect extends HTMLElement {
  constructor() {
    super();
    this.native=document.createElement('select');this.native.hidden=true;this.native.tabIndex=-1;
    this.trigger=document.createElement('button');this.trigger.type='button';this.trigger.className='select-trigger';
    this.trigger.setAttribute('role','combobox');this.trigger.setAttribute('aria-haspopup','listbox');this.trigger.setAttribute('aria-expanded','false');
    this.panel=document.createElement('div');this.panel.className='select-options';this.panel.hidden=true;this.panel.id=`choices-${crypto.randomUUID()}`;this.panel.setAttribute('role','listbox');
    this.trigger.setAttribute('aria-controls',this.panel.id);
    this.trigger.onclick=()=>this.panel.hidden?this.open():this.close();
    this.trigger.onkeydown=event=>{if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){event.preventDefault();event.stopPropagation();this.open(event.key)}};
    this.panel.onkeydown=event=>{const choices=[...this.panel.querySelectorAll('button:not(:disabled)')],index=choices.indexOf(document.activeElement);let next;
      if(event.key==='ArrowDown')next=choices[(index+1)%choices.length];else if(event.key==='ArrowUp')next=choices[(index-1+choices.length)%choices.length];else if(event.key==='Home')next=choices[0];else if(event.key==='End')next=choices.at(-1);else if(event.key==='Escape'||event.key==='ArrowLeft'||event.key==='ArrowRight'){this.close(true);event.preventDefault();event.stopPropagation();return;}else if(event.key==='Tab'){this.close();return;}else return;
      event.preventDefault();event.stopPropagation();next?.focus();next?.scrollIntoView?.({block:'nearest'});
    };
    this.outside=event=>{if(!this.contains(event.target))this.close()};
    this.scrolled=event=>{if(!this.panel.contains(event.target))this.close()};
    this.observer=new MutationObserver(()=>this.refresh());
    this.labels=new MutationObserver(()=>this.refresh());
  }
  connectedCallback(){if(this.trigger.parentNode!==this)super.append(this.native,this.trigger,this.panel);this.observer.observe(this.native,{childList:true,subtree:true,attributes:true,characterData:true});this.labels.observe(this,{attributes:true,attributeFilter:['aria-label','disabled']});document.addEventListener('pointerdown',this.outside,true);document.addEventListener('scroll',this.scrolled,true);this.refresh();}
  disconnectedCallback(){this.observer.disconnect();this.labels.disconnect();document.removeEventListener('pointerdown',this.outside,true);document.removeEventListener('scroll',this.scrolled,true);}
  append(...nodes){this.native.append(...nodes);queueMicrotask(()=>this.refresh());}
  replaceChildren(...nodes){this.native.replaceChildren(...nodes);queueMicrotask(()=>this.refresh());}
  get value(){return this.native.value;}
  set value(value){this.native.value=String(value);this.refresh();}
  get disabled(){return this.hasAttribute('disabled');}
  set disabled(value){this.toggleAttribute('disabled',!!value);this.refresh();}
  refresh(){this.trigger.textContent=this.native.selectedOptions[0]?.textContent||'Aucun choix disponible';this.trigger.disabled=this.disabled||!this.native.options.length;const label=this.getAttribute('aria-label')||'Choisir';this.trigger.setAttribute('aria-label',label);this.panel.setAttribute('aria-label',label);}
  open(key){if(this.trigger.disabled)return;this.panel.replaceChildren();for(const option of this.native.options){const button=document.createElement('button');button.type='button';button.setAttribute('role','option');button.setAttribute('aria-selected',String(option.value===this.value));button.textContent=option.textContent;button.disabled=option.disabled;button.onclick=()=>{const changed=this.value!==option.value;this.value=option.value;this.close(true);if(changed)this.dispatchEvent(new Event('change',{bubbles:true}));};this.panel.append(button);}
    this.panel.hidden=false;this.trigger.setAttribute('aria-expanded','true');const rect=this.getBoundingClientRect(),above=rect.top-8,below=innerHeight-rect.bottom-8,up=below<Math.min(this.panel.scrollHeight,280)&&above>below;const height=Math.max(40,Math.min(280,up?above:below));this.panel.style.position='fixed';this.panel.style.maxHeight=`${height}px`;this.panel.style.width=`${Math.min(Math.max(rect.width,180),innerWidth-16)}px`;this.panel.style.minWidth='0';this.panel.style.left=`${Math.max(8,Math.min(rect.left,innerWidth-this.panel.getBoundingClientRect().width-8))}px`;this.panel.style.top=`${up?Math.max(8,rect.top-this.panel.getBoundingClientRect().height-4):rect.bottom+4}px`;this.panel.style.bottom='auto';const choices=[...this.panel.querySelectorAll('button:not(:disabled)')],selected=this.panel.querySelector('[aria-selected="true"]');(key==='End'?choices.at(-1):key==='Home'?choices[0]:selected||choices[0])?.focus({preventScroll:true});
  }
  close(focus=false){const open=!this.panel.hidden;this.panel.hidden=true;this.trigger.setAttribute('aria-expanded','false');if(open&&focus)this.trigger.focus();}
}
customElements.define('sama-select',SamaSelect);
