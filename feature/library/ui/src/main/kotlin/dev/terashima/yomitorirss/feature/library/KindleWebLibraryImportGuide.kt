package dev.terashima.yomitorirss.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
  var showPersonalDocumentWebImport by rememberSaveable { mutableStateOf(false) }

  if (showWebImport) {
    AmazonWebLibraryImportDialog(
      source = LibrarySource.KINDLE,
      onDismiss = { showWebImport = false },
      onImportJson = importJson,
    )
  }

  if (showPersonalDocumentWebImport) {
    AmazonWebLibraryImportDialog(
      source = LibrarySource.KINDLE,
      onDismiss = { showPersonalDocumentWebImport = false },
      onImportJson = importJson,
      kindlePersonalDocuments = true,
    )
  }

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Kindle インポート", style = MaterialTheme.typography.titleMedium)
    Text(
      "購入済みの Kindle 本はアプリ内の専用 WebView から取り込めます。Amazon の認証情報はインポート処理へ渡しません。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = { showWebImport = true },
    ) {
      Text("アプリ内で Kindle 本を取り込む")
    }

    Text(
      "外部ブラウザ方式も利用できます。Chrome で Kindle Web Library を開き、ブックマークレットで JSON を保存してから上の Kindle インポートで選択します。",
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
        Text("Kindle Web Library")
      }
      OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = {
          copyBookmarklet(context, "Kindle Web Library exporter", KINDLE_WEB_LIBRARY_BOOKMARKLET)
        },
      ) {
        Text("ブックマークレット")
      }
    }

    HorizontalDivider()
    Text("Personal Document", style = MaterialTheme.typography.titleSmall)
    Text(
      "Send to Kindle などで追加した Personal Document もアプリ内の専用 WebView から取り込めます。ログイン後に「コンテンツと端末の管理」の Personal Document 一覧を開き、アプリが全件を取得してそのままインポートします。",
      style = MaterialTheme.typography.bodySmall,
    )
    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = { showPersonalDocumentWebImport = true },
    ) {
      Text("アプリ内で Personal Document を取り込む")
    }
    Text(
      "外部ブラウザとブックマークレットはフォールバックとして残します。通常本と Personal Document は別々に再インポートでき、片方を更新してももう片方は残ります。",
      style = MaterialTheme.typography.bodySmall,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = { uriHandler.openUri(KINDLE_PERSONAL_DOCUMENT_EXPORT_PAGE) },
      ) {
        Text("外部ブラウザで開く")
      }
      OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = {
          copyBookmarklet(context, "Kindle Personal Document exporter", KINDLE_PERSONAL_DOCUMENT_BOOKMARKLET)
        },
      ) {
        Text("ブックマークレット")
      }
    }
    Text(
      "Personal Document をタップすると Kindle アプリを起動し、タイトルをクリップボードへコピーします。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(
      "認証状態は専用 WebView プロファイル内だけに保持します。collector はログイン済み Amazon ページ内で動作し、Cookie、CSRF token、端末情報をインポート JSON へ含めません。Amazon 側の画面仕様変更により動作しなくなる場合は外部ブラウザ方式を利用できます。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun copyBookmarklet(
  context: android.content.Context,
  label: String,
  bookmarklet: String,
) {
  val clipboard = context.getSystemService(ClipboardManager::class.java)
  clipboard.setPrimaryClip(ClipData.newPlainText(label, bookmarklet))
  Toast.makeText(context, "ブックマークレットをコピーしました", Toast.LENGTH_SHORT).show()
}

internal const val KINDLE_WEB_LIBRARY_EXPORT_PAGE =
  "https://read.amazon.co.jp/kindle-library?resourceType=COMICS&tabView=series"

internal const val KINDLE_PERSONAL_DOCUMENT_EXPORT_PAGE =
  "https://www.amazon.co.jp/hz/mycd/digital-console/contentlist/pdocs/dateDsc/"

internal const val KINDLE_WEB_LIBRARY_BOOKMARKLET = """javascript:(async()=>{let d=document,p=d.body.appendChild(d.createElement('pre')),S=m=>new Promise(r=>setTimeout(r,m)),L=x=>p.textContent=x;p.style='position:fixed;z-index:999999999;left:8px;right:8px;bottom:8px;background:#fff;color:#111;padding:10px;border:2px solid #146eb4;max-height:40vh;overflow:auto';try{let F=async(t,k='')=>{let a=[],n=1,s=new Set;for(;;){if(s.has(k))break;s.add(k);L(t+' '+n+'ページ / '+a.length+'件');let u=new URL('/kindle-library/search',location.origin);u.searchParams.set('query','');u.searchParams.set('libraryType',t);u.searchParams.set('sortType','acquisition_asc');u.searchParams.set('querySize','50');if(k!=='')u.searchParams.set('paginationToken',k);let r=await fetch(u,{credentials:'include',cache:'no-store'});if(!r.ok)throw Error(t+' HTTP '+r.status);let j=await r.json(),x=j.itemsList||[];a.push(...x);let q=j.paginationToken==null?'':String(j.paginationToken);if(!x.length||!q||q==k)break;k=q;n++;await S(80)}return a};let N=new Map,C=()=>d.querySelectorAll('a[id^="library-series-item-option-"][href*="/kindle-library/manga/collection/"]').forEach(a=>{let m=a.href.match(/\/collection\/([^?/#]+)/),n=a.querySelector('img')?.alt?.trim();if(m&&n)N.set(m[1],n)}),e=d.getElementById('library');if(!e)throw Error('#library が見つかりません');let last=-1,z=0;for(let i=0;i<150&&z<6;i++){C();L('シリーズ名 '+N.size+'件');e.scrollTop=e.scrollHeight;await S(300);z=N.size==last?z+1:0;last=N.size}C();L('BOOKS取得中');let b=await F('BOOKS');L('MANGA取得中');let m=await F('MANGA','0'),M=new Map;m.forEach(x=>{let s=x.parentSeriesInfo;if(x.asin&&s?.asin)M.set(String(x.asin),{id:String(s.asin),name:N.get(String(s.asin))||null,position:s.positionInSeries??null})});let A=v=>[...new Set((Array.isArray(v)?v:[]).flatMap(x=>String(x).split(':')).map(x=>x.trim()).filter(Boolean))],B=new Map;b.forEach(x=>{if(!x.asin||x.resourceType!='EBOOK')return;let a=String(x.asin),s=M.get(a);B.set(a,{asin:a,title:String(x.title||'').trim(),authors:A(x.authors),coverUrl:x.productUrl?String(x.productUrl):null,series:s||null})});let books=[...B.values()],o={format:'kindle-library-export',version:1,exportedAt:new Date().toISOString(),count:books.length,stats:{seriesNames:N.size,seriesBooks:books.filter(x=>x.series).length,seriesBooksWithName:books.filter(x=>x.series?.name).length,missingCover:books.filter(x=>!x.coverUrl).length},books},u=URL.createObjectURL(new Blob([JSON.stringify(o,null,2)],{type:'application/json;charset=utf-8'}));p.innerHTML='完了 '+books.length+'冊 / シリーズ名 '+N.size+'件 / 表紙なし '+o.stats.missingCover+'冊<br><a id=k style="display:inline-block;margin-top:8px;padding:8px;background:#146eb4;color:white">JSONを保存</a>';let a=d.getElementById('k');a.href=u;a.download='kindle-library-export-'+new Date().toISOString().slice(0,10)+'.json'}catch(e){L('ERROR: '+e.message)}})()"""

internal const val KINDLE_PERSONAL_DOCUMENT_BOOKMARKLET = """javascript:(async()=>{let a=[],s=0,n=100,d=x=>{let e=document.createElement('textarea');e.innerHTML=x||'';return e.value};for(;;){let q={contentType:'KindlePDoc',contentCategoryReference:'pdocs',itemStatusList:['Active'],fetchCriteria:{sortOrder:'DESCENDING',sortIndex:'DATE',startIndex:s,batchSize:n,totalContentCount:-1},surfaceType:'Mobile'},p=new URLSearchParams({activity:'GetContentOwnershipData',activityInput:JSON.stringify(q),clientId:'MYCD_WebService',csrfToken:window.csrfToken}),r=await fetch('/hz/mycd/digital-console/ajax',{method:'POST',body:p}),j=await r.json(),g=j.GetContentOwnershipData||{},x=g.items||[];a.push(...x);if(!x.length||a.length>=+g.numberOfItems)break;s+=x.length}let b=a.map(x=>({id:x.asin,title:d(x.title).trim(),authors:x.author&&x.author!='Unknown'?[d(x.author).trim()]:[],contentType:x.contentType||null,acquiredAt:x.acquiredTime||null})).filter(x=>x.id&&x.title),o={format:'kindle-personal-library-export',version:1,exportedAt:new Date().toISOString(),count:b.length,books:b},u=URL.createObjectURL(new Blob([JSON.stringify(o,null,2)],{type:'application/json'})),e=document.createElement('a');e.href=u;e.download='kindle-personal-library-'+new Date().toISOString().slice(0,10)+'.json';e.click()})()"""
