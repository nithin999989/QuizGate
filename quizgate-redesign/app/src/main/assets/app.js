'use strict';
// Only packaged content runs here. Resume values are escaped, never treated as markup.
let state=null;
const esc=v=>String(v==null?'':v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const send=(action,payload={})=>LocalApp.send(action,JSON.stringify(payload));
const label=t=>`<div class="label">${esc(t)}</div>`;
const detail=t=>`<div class="detail">${esc(t)}</div>`;
const soft=t=>`<div class="soft">${esc(t)}</div>`;
const action=(t,a,primary=false,disabled=false)=>`<button class="action ${primary?'primary':''} ${disabled?'off':''}" data-action="${a}" ${disabled?'disabled':''}>${esc(t)}</button>`;
const header=(t,intro)=>`<header class="header"><h1>${esc(t)}</h1><p class="intro">${esc(intro)}</p></header>`;
const banner=s=>`<div class="banner">DEVELOPMENT<span>${s&&s.phoneWide?'Phone-wide session ON':'Phone-wide mode off'}</span></div>`;
const topbar=()=>`<div class="topbar"><div class="brand">QuizGate</div><button class="exit" data-action="end">End session</button></div>`;
const footer=()=>`<footer class="closing">Development build. Phone-wide popup runs only in sessions you start.</footer>`;
const steps=()=>['Choose a file in the Android document picker','Review and correct the detected skills/projects','Build a topic-matched practice set'].map((x,i)=>`<div class="step"><span class="step-num">0${i+1}</span>${detail(x)}</div>`).join('');
const skillChip=(skill,selected)=>`<button class="chip ${selected?'selected':''}" aria-pressed="${selected}" data-action="skill" data-value="${esc(skill)}">${esc(skill)}</button>`;
function home(s){
 const topics=[['Spring Boot','Transactions and production failures','SB'],['Microservices','Delivery, retries and resilience','MS'],['Redis','Caching and consistency','RD']];
 return header('QuizGate','Practice what you build.')+
 `<section class="section">${label('INTERVIEW PRACTICE')}<h2 class="group-heading">Your stack. Harder questions.</h2><div class="topics">`+
 topics.map(([name,sub,icon])=>`<button class="topic" data-action="topic" data-value="${name}" aria-pressed="${name===s.selectedTopic}"><span class="icon">${icon}</span><span class="topic-main"><span class="topic-title">${name}</span><span class="detail">${sub}</span></span><span class="count">10</span></button>`).join('')+
 `</div>${soft('30 sourced draft MCQs. Explanations plus interview follow-ups.')}</section>`+
 `<section class="section">${label('QUESTION INTERVAL')}<div class="chips">`+[5,10,15].map(n=>`<button class="chip ${n===s.interval?'selected':''}" data-action="interval" data-value="${n}" aria-pressed="${n===s.interval}">${n} minutes</button>`).join('')+`</div></section>`+
 `<section class="section">${label('MAKE IT YOURS')}<div class="panel"><h2 class="panel-title">From your resume</h2>${detail('Use real skills and projects to shape your practice set.')}${action(s.hasProfile?'Choose another resume':'Choose resume','pick')}${soft('Read locally. You review the extracted details.')}${s.hasProfile?action('Review skills and projects','review'):''}</div></section>`+
 action('Preview an interview question','preview',true)+footer()+
 soft(s.phoneWide?`Preview topic: ${s.selectedTopic}. Phone-wide timer is active.`:`Preview topic: ${s.selectedTopic}. The ${s.interval}-minute choice is saved; no background timer is active.`)+
 (s.note?`<div class="status" role="status">${esc(s.note)}</div>`:'')+
 (s.phoneWide?action('End phone-wide session','endSession')+soft('Phone-wide session is on. A question appears over other apps every '+s.interval+' minutes until you end it.'):action('Start phone-wide session','startSession')+soft('Only runs after you start it. Android asks for "Display over other apps" once; no accessibility or usage access.'))+soft(`Practice score: ${s.right} correct, ${s.wrong} incorrect.`)+
 (s.hasProfile?action('Clear resume details','clear'):'')+`<button class="link" data-action="licenses">Font licences</button>`;
}
function question(s){
 const q=s.question;
 if(!q)return '';
 return topbar()+label(q.topic.toUpperCase()+' / INTERVIEW PRACTICE')+detail(q.level+' scenario · '+q.id)+
 `<h1 class="question">${esc(q.q)}</h1><div class="answers">`+q.options.map((x,i)=>`<button class="answer" data-action="answer" data-index="${i}" ${s.answered?'disabled':''}><span class="letter">${String.fromCharCode(65+i)}</span><span>${esc(x)}</span></button>`).join('')+`</div>`+
 (s.answered?`<section class="feedback" id="feedback" tabindex="-1"><strong>Answer: ${esc(q.correctAnswer)}</strong><p>${esc(q.explanation)}</p><p class="timer" id="timer">Returning in ${s.remaining} seconds</p><button class="link" data-action="end">End now</button></section>`:'')+
 `<div class="divider"></div>`+detail('Correct: return immediately. Wrong: a 10-second explanation, then return.')+soft('Follow-up: '+q.followUp)+
 `<details><summary>Question sources</summary>${q.sources.map(x=>`<p>${esc(x)}</p>`).join('')}</details>`+footer();
}
function review(s){
 const order=['Spring Boot','Microservices','Redis','Kafka','gRPC','Resilience4j'];
 const skills=[...s.skills].sort((a,b)=>(order.indexOf(a)<0?99:order.indexOf(a))-(order.indexOf(b)<0?99:order.indexOf(b)));
 const other=s.allSkills.filter(x=>!s.skills.includes(x));
 return header('Practice from your resume','Review the details before making a set.')+
 `<div class="file">${label('SELECTED FILE')}<div class="file-name">${esc(s.sourceName)}</div>${soft('Read on this device. Contact details are not kept.')}</div>`+
 `<section class="section">${label('DETECTED SKILLS · TAP TO REMOVE')}<div class="chips">${skills.map(x=>skillChip(x,true)).join('')||detail('No supported skills detected. Add the skills you want to practise.')}</div><details><summary>Add or restore skills</summary><div class="chips">${other.map(x=>skillChip(x,false)).join('')}</div></details></section>`+
 `<section class="section">${label('PROJECTS · REVIEW THE SUGGESTED TITLES')}${s.projects.map(x=>`<div class="project-row">${esc(x)}</div>`).join('')||detail('No project titles detected. Add your own below.')}<details><summary>Edit project titles</summary><label class="detail" for="project-edit">One title per line. Remove contacts or incorrect lines.</label><textarea id="project-edit" maxlength="4000">${esc(s.projects.join('\n'))}</textarea>${action('Save project titles','saveProjects')}</details></section>`+
 (s.projects.length?`<section class="section">${label('EXAMPLE FOLLOW-UP')}<div class="panel">${label(s.projects[0].toUpperCase())}<p class="question-small">${esc(s.projectPrompt)}</p>${soft(s.promptCount+' local discussion templates. No personal answers invented.')}</div></section>`:'')+
 steps()+action('Review practice set','profile',true)+soft('Auto-detected details may contain OCR/matching errors. This is local bank matching and project templates, not cloud AI generation.')+footer()+action('Clear these details','clear')+`<button class="link" data-action="end">End / home</button>`;
}
function profile(s){
 return topbar()+header('Your practice set','Built from the details you reviewed.')+`<div class="panel"><h2 class="panel-title">${s.matchedCount} matched MCQs</h2>${detail(s.matchedTopics.join(' · ')||'No supported question topic matched.')}</div>`+
 soft('Matching uses the sourced 30-question bank. Other skills do not imply a dedicated question bank.')+action('Practice matched questions','matched',true,s.matchedCount===0)+
 (s.promptCount?`<section class="section"><div class="panel"><h2 class="panel-title">${s.promptCount} project discussion prompts</h2>${detail('Local templates use your reviewed titles. Discussion is for self-review, not automatic grading.')}${action('Open project prompt','project')}</div></section>`:'')+
 action('Edit reviewed details','review')+action('Clear resume details','clear')+footer();
}
function project(s){return topbar()+label('PROJECT PRACTICE · '+s.projectPosition+' OF '+s.promptCount)+`<h1 class="question">${esc(s.projectPrompt)}</h1><details><summary>Show discussion checklist</summary><div class="panel">${esc(s.projectChecklist)}</div></details>`+action('Next project prompt','nextProject',true)+action('Back to practice set','profile')+soft('Discussion, self-review only. No automatic right/wrong grading.')+footer();}
window.setState=function(s){
 const previous=state;state=s;
 if(previous&&previous.screen==='question'&&s.screen==='question'&&previous.answered&&s.answered&&previous.question.id===s.question.id){const timer=document.getElementById('timer');if(timer)timer.textContent='Returning in '+s.remaining+' seconds';return;}
 const y=window.scrollY;
 let page='';
 if(s.screen==='home')page=home(s);
 else if(s.screen==='question')page=question(s);
 else if(s.screen==='review')page=review(s);
 else if(s.screen==='profile')page=profile(s);
 else if(s.screen==='project')page=project(s);
 else if(s.screen==='reading')page=topbar()+header('Reading on this device','The selected file is not uploaded.')+`<progress aria-label="Reading selected resume"></progress>`+detail('PDFs use bundled on-device OCR. Up to 2 MB and 5 pages. Text recognition can make mistakes.')+soft('The temporary PDF is deleted when processing finishes. Reviewed skills and project titles stay in memory only.');
 else if(s.screen==='licenses')page=topbar()+header('Font licences','Newsreader and Inter are bundled under the SIL Open Font License 1.1.')+`<pre>${esc(s.licenses)}</pre>`;
 document.getElementById('app').innerHTML=banner(s)+page;
 if(!previous||previous.screen!==s.screen)window.scrollTo(0,0);else window.scrollTo(0,y);
 if(s.screen==='question'&&s.answered){const feedback=document.getElementById('feedback');feedback.scrollIntoView();feedback.focus({preventScroll:true});}
};
document.addEventListener('click',function(event){
 const b=event.target.closest('button[data-action]');if(!b||b.disabled)return;
 const a=b.dataset.action;
 if(a==='saveProjects'){const edit=document.getElementById('project-edit');if(edit)send('projects',{value:edit.value});}
 else if(a==='answer')send(a,{index:Number(b.dataset.index)});
 else if(a==='topic'||a==='skill')send(a,{value:b.dataset.value});
 else if(a==='interval')send(a,{value:Number(b.dataset.value)});
 else send(a);
});
