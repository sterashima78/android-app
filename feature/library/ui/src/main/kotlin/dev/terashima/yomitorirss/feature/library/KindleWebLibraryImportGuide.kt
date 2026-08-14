package dev.terashima.yomitorirss.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
internal fun KindleWebLibraryImportGuide() {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Kindle インポートデータの作成", style = MaterialTheme.typography.titleMedium)
    Text(
      "Amazon の Kindle Web Library 上でブックマークレットを実行し、蔵書・表紙・シリーズ情報を JSON に保存してからインポートします。Amazon の認証情報や Cookie はアプリへ渡しません。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      "1. Chrome で Amazon にログインし、下の「Kindle Web Library を開く」からシリーズ画面を開きます。\n" +
        "2. ブラウザでブックマークを作成し、URL を下のボタンでコピーしたブックマークレットに置き換えます。\n" +
        "3. Kindle Web Library のシリーズ画面を開いたまま、アドレスバーにブックマーク名を入力し、★付きの候補を選んで実行します。\n" +
        "4. 「完了 … / JSONを保存」と表示されたら「JSONを保存」を押します。\n" +
        "5. この画面へ戻り、Kindle の「インポート」から kindle-library-export-YYYY-MM-DD.json を選びます。",
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
        Text("Kindle Web Library を開く")
      }
      Button(
        modifier = Modifier.weight(1f),
        onClick = {
          val clipboard = context.getSystemService(ClipboardManager::class.java)
          clipboard.setPrimaryClip(
            ClipData.newPlainText("Kindle Web Library exporter", KINDLE_WEB_LIBRARY_BOOKMARKLET),
          )
          Toast.makeText(context, "ブックマークレットをコピーしました", Toast.LENGTH_SHORT).show()
        },
      ) {
        Text("ブックマークレットをコピー")
      }
    }
    Text(
      "ブックマークレットはログイン済みの read.amazon.co.jp 内だけで蔵書情報を取得し、端末へ JSON を保存します。Amazon 側の画面仕様変更により動作しなくなる場合があります。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }

  Spacer(Modifier.height(20.dp))
  AudibleWebLibraryImportGuide()
}

internal const val KINDLE_WEB_LIBRARY_EXPORT_PAGE =
  "https://read.amazon.co.jp/kindle-library?resourceType=COMICS&tabView=series"

internal const val KINDLE_WEB_LIBRARY_BOOKMARKLET = """javascript:(async()=>{let d=document,p=d.body.appendChild(d.createElement('pre')),S=m=>new Promise(r=>setTimeout(r,m)),L=x=>p.textContent=x;p.style='position:fixed;z-index:999999999;left:8px;right:8px;bottom:8px;background:#fff;color:#111;padding:10px;border:2px solid #146eb4;max-height:40vh;overflow:auto';try{let F=async(t,k='')=>{let a=[],n=1,s=new Set;for(;;){if(s.has(k))break;s.add(k);L(t+' '+n+'ページ / '+a.length+'件');let u=new URL('/kindle-library/search',location.origin);u.searchParams.set('query','');u.searchParams.set('libraryType',t);u.searchParams.set('sortType','acquisition_asc');u.searchParams.set('querySize','50');if(k!=='')u.searchParams.set('paginationToken',k);let r=await fetch(u,{credentials:'include',cache:'no-store'});if(!r.ok)throw Error(t+' HTTP '+r.status);let j=await r.json(),x=j.itemsList||[];a.push(...x);let q=j.paginationToken==null?'':String(j.paginationToken);if(!x.length||!q||q==k)break;k=q;n++;await S(80)}return a};let N=new Map,C=()=>d.querySelectorAll('a[id^="library-series-item-option-"][href*="/kindle-library/manga/collection/"]').forEach(a=>{let m=a.href.match(/\/collection\/([^?/#]+)/),n=a.querySelector('img')?.alt?.trim();if(m&&n)N.set(m[1],n)}),e=d.getElementById('library');if(!e)throw Error('#library が見つかりません');let last=-1,z=0;for(let i=0;i<150&&z<6;i++){C();L('シリーズ名 '+N.size+'件');e.scrollTop=e.scrollHeight;await S(300);z=N.size==last?z+1:0;last=N.size}C();L('BOOKS取得中');let b=await F('BOOKS');L('MANGA取得中');let m=await F('MANGA','0'),M=new Map;m.forEach(x=>{let s=x.parentSeriesInfo;if(x.asin&&s?.asin)M.set(String(x.asin),{id:String(s.asin),name:N.get(String(s.asin))||null,position:s.positionInSeries??null})});let A=v=>[...new Set((Array.isArray(v)?v:[]).flatMap(x=>String(x).split(':')).map(x=>x.trim()).filter(Boolean))],B=new Map;b.forEach(x=>{if(!x.asin||x.resourceType!='EBOOK')return;let a=String(x.asin),s=M.get(a);B.set(a,{asin:a,title:String(x.title||'').trim(),authors:A(x.authors),coverUrl:x.productUrl?String(x.productUrl):null,series:s||null})});let books=[...B.values()],o={format:'kindle-library-export',version:1,exportedAt:new Date().toISOString(),count:books.length,stats:{seriesNames:N.size,seriesBooks:books.filter(x=>x.series).length,seriesBooksWithName:books.filter(x=>x.series?.name).length,missingCover:books.filter(x=>!x.coverUrl).length},books},u=URL.createObjectURL(new Blob([JSON.stringify(o,null,2)],{type:'application/json;charset=utf-8'}));p.innerHTML='完了 '+books.length+'冊 / シリーズ名 '+N.size+'件 / 表紙なし '+o.stats.missingCover+'冊<br><a id=k style="display:inline-block;margin-top:8px;padding:8px;background:#146eb4;color:white">JSONを保存</a>';let a=d.getElementById('k');a.href=u;a.download='kindle-library-export-'+new Date().toISOString().slice(0,10)+'.json'}catch(e){L('ERROR: '+e.message)}})()"""
