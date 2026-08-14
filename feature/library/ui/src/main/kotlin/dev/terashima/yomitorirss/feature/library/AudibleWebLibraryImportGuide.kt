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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
internal fun AudibleWebLibraryImportGuide() {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Audible インポートデータの作成", style = MaterialTheme.typography.titleMedium)
    Text(
      "Audible Web Library から全蔵書の ASIN を取得し、Audible のカタログ情報でタイトル・著者・ナレーター・表紙・再生時間・シリーズを補完して JSON に保存します。ログイン情報や Cookie は JSON に含めません。",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      "1. Chrome で Audible にログインし、下の「Audible Library を開く」からライブラリーを開きます。\n" +
        "2. 1つ目のブックマークを作成し、「① ASIN収集」をコピーして URL に設定します。ライブラリー上で実行すると全ページを巡回して API の JSON 画面へ移動します。\n" +
        "3. 2つ目のブックマークを作成し、「② JSON出力」をコピーして URL に設定します。API の JSON 画面上で実行すると残りの ASIN を50件ずつ取得し、シリーズ情報を補完して JSON を保存します。\n" +
        "4. この画面へ戻り、Audible の「インポート」から audible-library-export-YYYY-MM-DD.json を選びます。",
      style = MaterialTheme.typography.bodySmall,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        modifier = Modifier.weight(1f),
        onClick = { uriHandler.openUri(AUDIBLE_WEB_LIBRARY_EXPORT_PAGE) },
      ) {
        Text("Audible Library を開く")
      }
      Button(
        modifier = Modifier.weight(1f),
        onClick = {
          copyBookmarklet(
            context = context,
            label = "Audible Web Library ASIN collector",
            value = AUDIBLE_WEB_LIBRARY_COLLECT_BOOKMARKLET,
            message = "① ASIN収集をコピーしました",
          )
        },
      ) {
        Text("① ASIN収集")
      }
    }
    Button(
      modifier = Modifier.fillMaxWidth(),
      onClick = {
        copyBookmarklet(
          context = context,
          label = "Audible Web Library JSON exporter",
          value = AUDIBLE_WEB_LIBRARY_EXPORT_BOOKMARKLET,
          message = "② JSON出力をコピーしました",
        )
      },
    ) {
      Text("② JSON出力をコピー")
    }
    Text(
      "Audible のライブラリー画面とカタログ API は別オリジンのため2段階で実行します。カタログ API は公開仕様として保証された API ではなく、Audible 側の変更で動作しなくなる場合があります。従来の Library.csv / ZIP も引き続きインポートできます。",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun copyBookmarklet(
  context: android.content.Context,
  label: String,
  value: String,
  message: String,
) {
  val clipboard = context.getSystemService(ClipboardManager::class.java)
  clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
  Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

internal const val AUDIBLE_WEB_LIBRARY_EXPORT_PAGE =
  "https://www.audible.co.jp/library/titles"

internal const val AUDIBLE_WEB_LIBRARY_COLLECT_BOOKMARKLET = """javascript:(async()=>{try{let S=new Set;for(let p=1;p<=200;p++){let r=await fetch('/library/titles?page='+p,{credentials:'include'});if(!r.ok)break;let d=new DOMParser().parseFromString(await r.text(),'text/html'),A=[...d.querySelectorAll('[data-asin]')].map(e=>e.dataset.asin).filter(x=>/^[A-Z0-9]{10}$/i.test(x)),n=S.size;A.forEach(x=>S.add(x.toUpperCase()));if(!A.length||S.size==n)break}let A=[...S],F=A.slice(0,50),R=A.slice(50),u='https://api.audible.co.jp/1.0/catalog/products?asins='+F.join(',')+'&response_groups=contributors,media,product_desc,product_extended_attrs,relationships&image_sizes=500';if(!A.length)return alert('ASIN 0件');alert(A.length+'件取得');location.href=u+'#rest='+R.join(',')}catch(e){alert('取得失敗: '+e.message)}})()"""

internal const val AUDIBLE_WEB_LIBRARY_EXPORT_BOOKMARKLET = """javascript:(async()=>{try{let P=JSON.parse(document.body.innerText).products||[],R=(location.hash.match(/rest=([^#]+)/)?.[1]||'').split(',').filter(Boolean),G='contributors,media,product_desc,product_extended_attrs,relationships';for(let i=0;i<R.length;i+=50){let A=R.slice(i,i+50),j=await fetch('/1.0/catalog/products?asins='+A.join(',')+'&response_groups='+G+'&image_sizes=500').then(r=>r.json());P.push(...(j.products||[]))}let H=s=>{if(!s)return null;let d=new DOMParser().parseFromString('<body>'+s,'text/html');return d.body.textContent.replace(/\s+/g,' ').trim()},C=p=>p.product_images?.['500']||Object.entries(p.product_images||{}).sort((a,b)=>+b[0]-+a[0])[0]?.[1]||null,S=new Map;for(let i=0;i<P.length;i+=5){await Promise.all(P.slice(i,i+5).map(async p=>{try{let j=await fetch('/1.0/catalog/products/'+p.asin+'?response_groups=series').then(r=>r.json()),q=j.product?.series?.[0];if(!q)return;let n=q.title||q.name||null,v=parseInt(q.sequence),pos=Number.isFinite(v)?v:null,id=/^[A-Z0-9]{10}$/i.test(q.asin||'')?String(q.asin).toUpperCase():null;if((n||id)&&!(n&&pos==null&&/(出版社|出版|パブリッシング|publishing|press)$/i.test(n)))S.set(p.asin,{id:id,name:n,position:pos})}catch{}}))}let B=P.map(p=>({asin:p.asin,title:p.title,subtitle:p.subtitle||null,authors:(p.authors||[]).map(x=>x.name),narrators:(p.narrators||[]).map(x=>x.name),publisher:p.publisher_name||p.publication_name||null,publishedDate:p.release_date||p.issue_date||null,description:H(p.merchandising_summary||p.publisher_summary),coverUrl:C(p),durationMinutes:p.runtime_length_min||null,series:S.get(p.asin)||null,productUrl:'https://www.audible.co.jp/pd/'+p.asin})),o={format:'audible-library-export',version:1,exportedAt:new Date().toISOString(),count:B.length,stats:{seriesBooks:B.filter(x=>x.series).length,missingCover:B.filter(x=>!x.coverUrl).length,missingPublisher:B.filter(x=>!x.publisher).length},books:B},j=JSON.stringify(o,null,2),u=URL.createObjectURL(new Blob([j],{type:'application/json;charset=utf-8'})),a=document.createElement('a');a.href=u;a.download='audible-library-export-'+new Date().toISOString().slice(0,10)+'.json';a.click();setTimeout(()=>URL.revokeObjectURL(u),1000);alert(B.length+'件を出力しました / シリーズ '+o.stats.seriesBooks+'件')}catch(e){alert('失敗: '+e.message)}})()"""
