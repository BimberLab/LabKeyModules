Ext4.define('SequenceAnalysis.window.ImportChainFileWindow', {
    extend: 'Ext.window.Window',

    statics: {
        buttonHandler: function(dataRegionName){
            Ext4.create('SequenceAnalysis.window.ImportChainFileWindow', {
                dataRegionName: dataRegionName
            }).show();
        }
    },

    initComponent: function(){
        Ext4.apply(this, {
            modal: true,
            title: 'Import Chain File',
            width: 600,
            bodyStyle: 'padding: 5px;',
            defaults: {
                border: false,
                width: 450
            },
            items: [{
                html: 'This will import a chain file, which is used to liftover variants or annotations from one reference genome to another. Note: because UCSC is a common source of chain files and they use non-standard contig names, the server-side code will attempt to parse any non-standard names in the chain file, match them with the genomes, and write an updated chain file.',
                style: 'padding-bottom: 10px;',
                width: null
            },{
                xtype: 'form',
                url: LABKEY.ActionURL.buildURL('sequenceanalysis', 'importChainFile', null, null),
                fileUpload: true,
                defaults: {
                    border: false,
                    width: 450,
                    labelWidth: 150
                },
                items: [{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Source Genome',
                    name: 'genomeId1',
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'reference_libraries',
                    filterArray: [LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)],
                    displayField: 'name',
                    valueField: 'rowid',
                    value: LABKEY.ActionURL.getParameter('libraryId') ? parseInt(LABKEY.ActionURL.getParameter('libraryId')) : null
                },{
                    xtype: 'ldk-simplelabkeycombo',
                    fieldLabel: 'Target Genome',
                    name: 'genomeId2',
                    containerPath: Laboratory.Utils.getQueryContainerPath(),
                    schemaName: 'sequenceanalysis',
                    queryName: 'reference_libraries',
                    filterArray: [LABKEY.Filter.create('datedisabled', null, LABKEY.Filter.Types.ISBLANK)],
                    displayField: 'name',
                    valueField: 'rowid'
                },{
                    xtype: 'filefield',
                    fieldLabel: 'File',
                    name: 'chainFile',
                    allowBlank: false
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Source',
                    name: 'source',
                    allowBlank: false
                },{
                    xtype: 'ldk-numberfield',
                    fieldLabel: 'Version',
                    helpPopup: 'This field is used by the system - when a chain file is being selected automatically it will choose the one with the highest version',
                    name: 'version',
                    allowBlank: false
                },{
                    xtype: 'checkbox',
                    fieldLabel: 'Allow Unknown Contigs',
                    helpPopup: 'By default, if the chain file contains contigs that cannot be mapped to contigs in the genomes, it will fail. If this is checked, these chains will simply be discarded.',
                    name: 'allowUnknownContig',
                    checked: false
                },{
                    xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF
                }]
            }],
            buttons: [{
                text: 'Submit',
                scope: this,
                handler: this.onSubmit
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').close();
                }
            }]
        });

        this.callParent(arguments);
    },

    onSubmit: function(btn){
        var fasta = this.down('filefield[name=chainFile]').getValue();
        if (!fasta){
            Ext4.Msg.alert('Error', 'Must provide a file');
            return;
        }

        if (!this.down('field[name=genomeId1]').getValue() || !this.down('field[name=genomeId1]').getValue()){
            Ext4.Msg.alert('Error', 'Must provide the source and target genomes');
            return;
        }

        if (!this.down('form').isValid()){
            Ext4.Msg.alert('Error', 'There are errors in the form.  Hover over the red fields for more information.');
            return;
        }

        Ext4.Msg.wait('Loading...');
        this.down('form').submit({
            scope: this,
            timeout: 999999999,
            success: function(form, action){
                Ext4.Msg.hide();

                var msg = null;
                if (action && action.response && action.response.responseText){
                    if (Ext4.String.startsWith(action.response.responseText, '{')) {
                        action.response.responseJSON = Ext4.decode(action.response.responseText);
                    }

                    if (action.response.responseJSON && action.response.responseJSON.messages) {
                        console.log(action.response.responseJSON.messages);
                        msg = action.response.responseJSON.messages.join('<br>');
                    }
                }

                this.close();

                Ext4.Msg.alert('Success', 'Chain File Imported!' + (msg ? ' Notes:<br><br>' + msg : ''), function(){
                    var dataRegion = LABKEY.DataRegions[this.dataRegionName];
                    dataRegion.refresh();
                }, this);
            },
            failure: function(form, action){
                Ext4.Msg.hide();
                var msg;
                var serverMsg = [];
                if (action && action.response && action.response.responseText){
                    if (Ext4.String.startsWith(action.response.responseText, '{')) {
                        action.response.responseJSON = Ext4.decode(action.response.responseText);
                    }

                    if (action.response.responseJSON && action.response.responseJSON.exception) {
                        msg = action.response.responseJSON.exception;
                    }
                    else {
                        msg = action.response.responseText;
                    }
                    serverMsg.push(action.response.responseText);
                }

                if (!msg && action && action.result && action.result.exception){
                    msg = action.result.exception;
                    serverMsg.push(Ext4.encode(action.result));
                }

                if (!msg){
                    msg = 'There was an error uploading this file';
                }

                Ext4.Msg.alert('Error', msg);
                LDK.Utils.logError('Problem uploading chain file: ' + serverMsg.join(';'));
                console.error(serverMsg);
            }
        });
    }
});
