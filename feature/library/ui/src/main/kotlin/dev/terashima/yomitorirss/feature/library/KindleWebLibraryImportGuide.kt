package dev.terashima.yomitorirss.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
internal fun KindleWebLibraryImportGuide() {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  val importJson = LocalWebLibraryImportHandler.current
  var showWebImport by rememberSaveable { mutableStateOf(false) }
  var showPersonalDocumentDeepLinkTest by rememberSaveable { mutableStateOf(false) }

  if (showWebImport) {
    AmazonWebLibraryImportDialog(
      source = LibrarySource.KINDLE,
      onDismiss = { showWebImport = false },
      onImportJson = importJson,
    )
  }

  if (showPersonalDocumentDeepLinkTest) {
    KindlePersonalDocumentDeepLinkTestScreen(
      onDismiss = { showPersonalDocumentDeepLinkTest = false },
    )
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Kindle インポート", style = MaterialTheme.typography.titleMedium)
    Text(
      "通常はアプリ内の専用 WebView で Amazon にログインし、そのまま蔵書・表紙・シリーズ情報を取り込みます。ログイン状態は Amazon インポート専用の WebView プロファイルに保持されます。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = { showWebImport = true },
    ) {
      Text("アプリ内で Kindle を取り込む")
    }

    Text(
      "外部ブラウザ方式もフォールバックとして利用できます。Chrome で Kindle Web Library を開き、ブックマークレットを実行して JSON を保存した後、上の Kindle インポートからファイルを選択してください。",
      style = MaterialTheme.typography.bodySmall,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = { uriHandler.openUri(KINDLE_WEB_LIBRARY_EXPORT_PAGE) },
      ) {
        Text("外部ブラウザで開く")
      }
      OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = {
          val clipboard = context.getSystemService(ClipboardManager::class.java)
          clipboard.setPrimaryClip(
            ClipData.newPlainText("Kindle Web Library exporter", KINDLE_WEB_LIBRARY_BOOKMARKLET),
          )
          Toast.makeText(context, "ブックマークレットをコピーしました", Toast.LENGTH_SHORT).show()
        },
      ) {
        Text("ブックマークレット")
      }
    }
    OutlinedButton(
      modifier = Modifier.fillMaxWidth(),
      onClick = { showPersonalDocumentDeepLinkTest = true },
    ) {
      Text("Personal Document リンク検証")
    }
    Text(
      "アプリは Amazon のパスワードや Cookie を読み取りません。WebView 内で生成した蔵書 JSON だけを既存の Kindle インポーターへ渡します。Amazon 側の画面仕様変更で取得できなくなった場合は外部ブラウザ方式を利用できます。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

internal const val KINDLE_WEB_LIBRARY_EXPORT_PAGE =
  "https://read.amazon.co.jp/kindle-library?resourceType=COMICS&tabView=series"

internal const val KINDLE_WEB_LIBRARY_BOOKMARKLET = """javascript:(async()=>{let d=document,p=d.body.appendChild(d.createElement('pre')),S=m=>new Promise(r=>setTimeout(r,m)),L=x=>p.textContent=x;p.style='position:fixed;z-index:999999999;left:8px;right:8px;bottom:8px;background:#fff;color:#111;padding:10px;border:2px solid #146eb4;max-height:40vh;overflow:auto';try{let F=async(t,k='')=>{let a=[],n=1,s=new Set;for(;;){if(s.has(k))break;s.add(k);L(t+' '+n+'ページ / '+a.length+'件');let u=new URL('/kindle-library/search',location.origin);u.searchParams.set('query','');u.searchParams.set('libraryType',t);u.searchParams.set('sortType','acquisition_asc');u.searchParams.set('querySize','50');if(k!=='')u.searchParams.set('paginationToken',k);let r=await fetch(u,{credentials:'include',cache:'no-store'});if(!r.ok)throw Error(t+' HTTP '+r.status);let j=await r.json(),x=j.itemsList||[];a.push(...x);let q=j.paginationToken==null?'':String(j.paginationToken);if(!x.length||!q||q==k)break;k=q;n++;await S(80)}return a};let N=new Map,C=()=>d.querySelectorAll('a[id^="library-series-item-option-"][href*="/kindle-library/manga/collection/"]').forEach(a=>{let m=a.href.match(/\/collection\/([^?/#]+)/),n=a.querySelector('img')?.alt?.trim();if(m&&n)N.set(m[1],n)}),e=d.getElementById('library');if(!e)throw Error('#library が見つかりません');let last=-1,z=0;for(let i=0;i<150&&z<6;i++){C();L('シリーズ名 '+N.size+'件');e.scrollTop=e.scrollHeight;await S(300);z=N.size==last?z+1:0;last=N.size}C();L('BOOKS取得中');let b=await F('BOOKS');L('MANGA取得中');let m=await F('MANGA','0'),M=new Map;m.forEach(x=>{let s=x.parentSeriesInfo;if(x.asin&&s?.asin)M.set(String(x.asin),{id:String(s.asin),name:N.get(String(s.asin))||null,position:s.positionInSeries??null})});let A=v=>[...new Set((Array.isArray(v)?v:[]).flatMap(x=>String(x).split(':')).map(x=>x.trim()).filter(Boolean))],B=new Map;b.forEach(x=>{if(!x.asin||x.resourceType!='EBOOK')return;let a=String(x.asin),s=M.get(a);B.set(a,{asin:a,title:String(x.title||'').trim(),authors:A(x.authors),coverUrl:x.productUrl?String(x.productUrl):null,series:s||null})});let books=[...B.values()],o={format:'kindle-library-export',version:1,exportedAt:new Date().toISOString(),count:books.length,stats:{seriesNames:N.size,seriesBooks:books.filter(x=>x.series).length,seriesBooksWithName:books.filter(x=>x.series?.name).length,missingCover:books.filter(x=>!x.coverUrl).length},books},u=URL.createObjectURL(new Blob([JSON.stringify(o,null,2)],{type:'application/json;charset=utf-8'}));p.innerHTML='完了 '+books.length+'冊 / シリーズ名 '+N.size+'件 / 表紙なし '+o.stats.missingCover+'冊<br><a id=k style="display:inline-block;margin-top:8px;padding:8px;background:#146eb4;color:white">JSONを保存</a>';let a=d.getElementById('k');a.href=u;a.download='kindle-library-export-'+new Date().toISOString().slice(0,10)+'.json'}catch(e){L('ERROR: '+e.message)}})()"""
